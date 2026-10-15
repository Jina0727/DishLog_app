"""
Dishlog 모델 비교 실험 (GitHub Actions 에서 실행).

데이터: Nutrition5k (Google) — 실제 식당 접시를 재료별로 계량해 계산한 칼로리가 정답.
비교: 앱과 같은 프롬프트로 Sonnet 5.5 vs Haiku 5.5.
결과: lab/results.md (요약), lab/results.csv (접시별), lab/summary.txt (한 줄 요약)

사용: ANTHROPIC_API_KEY 환경변수 필요.  python lab/run_lab.py --n 100
"""
import argparse
import base64
import csv
import io
import json
import os
import random
import re
import statistics
import time
import urllib.error
import urllib.request
from concurrent.futures import ThreadPoolExecutor

from PIL import Image

BASE = "https://storage.googleapis.com/nutrition5k_dataset/nutrition5k_dataset"
META = [f"{BASE}/metadata/dish_metadata_cafe1.csv", f"{BASE}/metadata/dish_metadata_cafe2.csv"]
IDS = f"{BASE}/dish_ids/splits/rgb_test_ids.txt"
IMG = BASE + "/imagery/realsense_overhead/{dish}/rgb.png"

MODELS = {
    "Sonnet 5.5": ("claude-sonnet-5-5", 2.0, 10.0),
    "Haiku 5.5": ("claude-haiku-5-5", 0.10, 0.50),
}

# 앱(CalorieEstimator.kt)과 같은 프롬프트
PROMPT = """
이 사진들은 한 사람이 한 번에 먹은 식사(또는 간식·음료)를 찍은 것이에요.
그릇이 여러 개이거나 여러 장에 나눠 찍혔을 수 있어요. 같은 음식이 여러 장에 보이면 한 번만 세세요.

음식·음료마다 한국어 이름과 실제로 먹는 양 기준 칼로리(kcal, 정수)를 구하세요.
- 포장 제품이면 포장지·영양성분표에 적힌 칼로리를 가장 먼저 읽고 그대로 쓰세요. 제품명도 그대로 쓰세요.
- 우유·물에 타 먹는 쉐이크, 끓여 먹는 라면처럼 먹는 방법이 정해진 제품은
  포장지의 조리법(없으면 가장 일반적인 방법)대로 먹었다고 보고, 더해지는 재료(예: 우유 200ml)의 칼로리를
  별도 항목으로 넣으세요.
- 조리된 음식은 그릇·수저 크기를 단서로 양을 가늠하세요.
- 각 항목의 note 에는 근거나 가정을 짧게 적으세요 (예: "포장지 표기", "우유 200ml 기준", "1공기 210g 추정").

설명 없이 아래 JSON 형식으로만 답하세요. 음식이 없으면 items 를 빈 배열로 두세요.
{"items":[{"name":"단백질 쉐이크 파우치","kcal":158,"note":"포장지 표기"},{"name":"우유 200ml","kcal":130,"note":"조리법 기준 가정"}]}
"""


def fetch(url, timeout=60):
    with urllib.request.urlopen(url, timeout=timeout) as r:
        return r.read()


def load_truth():
    truth = {}
    for url in META:
        for line in fetch(url).decode().splitlines():
            parts = line.split(",")
            if len(parts) > 2:
                try:
                    truth[parts[0]] = (float(parts[1]), float(parts[2]))  # kcal, mass(g)
                except ValueError:
                    pass
    return truth


def image_b64(dish):
    png = fetch(IMG.format(dish=dish))
    img = Image.open(io.BytesIO(png)).convert("RGB")
    out = io.BytesIO()
    img.save(out, "JPEG", quality=90)
    return base64.b64encode(out.getvalue()).decode(), img.size


def call(model_id, b64, key, tries=5):
    body = json.dumps({
        "model": model_id,
        "max_tokens": 1024,
        "messages": [{"role": "user", "content": [
            {"type": "image", "source": {"type": "base64", "media_type": "image/jpeg", "data": b64}},
            {"type": "text", "text": PROMPT},
        ]}],
    }).encode()
    for attempt in range(tries):
        req = urllib.request.Request(
            "https://api.anthropic.com/v1/messages", data=body, method="POST",
            headers={"content-type": "application/json", "x-api-key": key, "anthropic-version": "2023-06-01"},
        )
        t0 = time.time()
        try:
            with urllib.request.urlopen(req, timeout=120) as r:
                resp = json.loads(r.read())
            secs = time.time() - t0
            text = "".join(b.get("text", "") for b in resp.get("content", []) if b.get("type") == "text")
            m = re.search(r"\{.*\}", text, re.S)
            items = json.loads(m.group(0)).get("items", []) if m else []
            u = resp.get("usage", {})
            return items, u.get("input_tokens", 0), u.get("output_tokens", 0), secs, ""
        except urllib.error.HTTPError as e:
            if e.code in (429, 500, 502, 503, 529) and attempt < tries - 1:
                time.sleep(2 ** attempt * 3)
                continue
            return None, 0, 0, 0, f"HTTP {e.code}: {e.read()[:200]!r}"
        except Exception as e:  # noqa: BLE001
            if attempt < tries - 1:
                time.sleep(3)
                continue
            return None, 0, 0, 0, str(e)[:200]


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--n", type=int, default=100)
    ap.add_argument("--seed", type=int, default=42)
    args = ap.parse_args()
    key = os.environ["ANTHROPIC_API_KEY"]

    truth = load_truth()
    ids = [i.strip() for i in fetch(IDS).decode().splitlines() if i.strip() in truth and truth[i.strip()][0] >= 30]
    dishes = random.Random(args.seed).sample(ids, min(args.n, len(ids)))
    print(f"{len(dishes)} dishes sampled from {len(ids)} candidates")

    def safe_image(d):
        try:
            return image_b64(d)
        except Exception as e:  # noqa: BLE001  사진이 없는 접시는 건너뛰어요
            print(f"skip {d}: {e}")
            return None

    images = {}
    with ThreadPoolExecutor(8) as ex:
        for d, res in zip(dishes, ex.map(safe_image, dishes)):
            if res is not None:
                images[d] = res
    dishes = [d for d in dishes if d in images]

    rows = []

    def job(args_):
        d, label = args_
        model_id = MODELS[label][0]
        items, tin, tout, secs, err = call(model_id, images[d][0], key)
        pred = sum(int(it.get("kcal", 0) or 0) for it in items) if items is not None else None
        return {
            "dish": d, "model": label, "true_kcal": round(truth[d][0], 1), "mass_g": round(truth[d][1], 1),
            "pred_kcal": pred, "items": "; ".join(f"{it.get('name')} {it.get('kcal')}" for it in (items or [])),
            "in_tok": tin, "out_tok": tout, "secs": round(secs, 2), "error": err,
        }

    jobs = [(d, label) for d in dishes for label in MODELS]
    with ThreadPoolExecutor(4) as ex:
        for i, r in enumerate(ex.map(job, jobs), 1):
            rows.append(r)
            if i % 20 == 0:
                print(f"{i}/{len(jobs)} done")

    os.makedirs("lab", exist_ok=True)
    with open("lab/results.csv", "w", newline="", encoding="utf-8") as f:
        w = csv.DictWriter(f, fieldnames=list(rows[0].keys()))
        w.writeheader()
        w.writerows(rows)

    # 요약
    lines = [
        "# 모델 비교 결과",
        "",
        f"- 데이터: Nutrition5k 위에서 찍은 접시 사진 {len(dishes)}개 (재료를 계량해 계산한 칼로리가 정답), 시드 {args.seed}",
        f"- 사진 크기: {images[dishes[0]][1][0]}×{images[dishes[0]][1][1]}px (원본 그대로)",
        f"- 정답 칼로리 중앙값: {statistics.median(truth[d][0] for d in dishes):.0f} kcal",
        "- 프롬프트: 앱과 동일",
        "",
        "| 모델 | 성공 | 평균 오차 | 오차율 중앙값 | ±20% 이내 | ±30% 이내 | 경향(평균 부호오차) | 장당 비용 | 장당 시간 |",
        "|---|---|---|---|---|---|---|---|---|",
    ]
    summary = []
    for label, (_, pin, pout) in MODELS.items():
        rs = [r for r in rows if r["model"] == label and r["pred_kcal"] is not None]
        if not rs:
            lines.append(f"| {label} | 0 | – | – | – | – | – | – | – |")
            continue
        abs_err = [abs(r["pred_kcal"] - r["true_kcal"]) for r in rs]
        ape = [abs(r["pred_kcal"] - r["true_kcal"]) / r["true_kcal"] for r in rs]
        signed = [r["pred_kcal"] - r["true_kcal"] for r in rs]
        cost = [(r["in_tok"] * pin + r["out_tok"] * pout) / 1e6 for r in rs]
        w20 = sum(a <= 0.2 for a in ape) / len(rs)
        w30 = sum(a <= 0.3 for a in ape) / len(rs)
        lines.append(
            f"| {label} | {len(rs)}/{len(dishes)} | {statistics.mean(abs_err):.0f} kcal | {statistics.median(ape)*100:.0f}% "
            f"| {w20*100:.0f}% | {w30*100:.0f}% | {statistics.mean(signed):+.0f} kcal "
            f"| ${statistics.mean(cost):.4f} | {statistics.mean(r['secs'] for r in rs):.1f}초 |"
        )
        summary.append(f"{label}: MAE {statistics.mean(abs_err):.0f}kcal, MdAPE {statistics.median(ape)*100:.0f}%, ±20% {w20*100:.0f}%, ${statistics.mean(cost):.4f}/장")
    errors = [r for r in rows if r["error"]]
    if errors:
        lines += ["", f"실패 {len(errors)}건 (예: {errors[0]['error']})"]
    lines += ["", "접시별 결과는 `results.csv`."]
    with open("lab/results.md", "w", encoding="utf-8") as f:
        f.write("\n".join(lines) + "\n")
    with open("lab/summary.txt", "w", encoding="utf-8") as f:
        f.write("\n".join(summary) + "\n")
    print("\n".join(lines))


if __name__ == "__main__":
    main()
