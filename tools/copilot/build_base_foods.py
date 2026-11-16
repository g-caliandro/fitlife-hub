#!/usr/bin/env python3
"""Builds app/src/main/assets/copilot/base_foods.json from USDA FoodData Central SR Legacy (CSV, April 2018, CC0 1.0).

Usage: python3 -I tools/copilot/build_base_foods.py <dir with food.csv, food_nutrient.csv>
Download: https://fdc.nal.usda.gov/fdc-datasets/FoodData_Central_sr_legacy_food_csv_2018-04.zip

Per curated fdc_id (tools/copilot/base_foods_curation.csv) it takes, per 100 g edible portion:
energy kcal (nutrient 1008), protein (1003), total fat (1004), carbohydrate by difference (1005).
Every entry must exist and have the four values; the script also checks kcal against 4P + 4C + 9F (warning only:
alcohol-free foods should be within 15 %, fibre-rich ones may differ). Output is sorted by id and deterministic.
"""
import csv
import json
import os
import sys

NUTRIENTS = {"1008": "kcal100", "1003": "protein100", "1004": "fat100", "1005": "carbs100"}
HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.dirname(os.path.dirname(HERE))
CURATION = os.path.join(HERE, "base_foods_curation.csv")
OUT = os.path.join(ROOT, "app", "src", "main", "assets", "copilot", "base_foods.json")


def main(src):
    rows = []
    with open(CURATION, encoding="utf-8") as f:
        for line in f:
            line = line.rstrip("\n")
            if not line or line.startswith("#") or line.startswith("fdc_id;"):
                continue
            fdc, name, aliases, state = line.split(";")
            rows.append((fdc.strip(), name.strip(), [a.strip() for a in aliases.split("|") if a.strip()], state.strip()))
    ids = {r[0] for r in rows}
    if len(ids) != len(rows):
        sys.exit("duplicate fdc_id in curation")

    desc = {}
    with open(os.path.join(src, "food.csv"), encoding="utf-8") as f:
        for r in csv.DictReader(f):
            if r["fdc_id"] in ids:
                desc[r["fdc_id"]] = r["description"]
    values = {i: {} for i in ids}
    with open(os.path.join(src, "food_nutrient.csv"), encoding="utf-8") as f:
        for r in csv.DictReader(f):
            if r["fdc_id"] in ids and r["nutrient_id"] in NUTRIENTS:
                values[r["fdc_id"]][NUTRIENTS[r["nutrient_id"]]] = round(float(r["amount"]), 2)

    out = []
    for fdc, name, aliases, state in rows:
        if fdc not in desc:
            sys.exit(f"fdc_id {fdc} not found in SR Legacy")
        v = values[fdc]
        if len(v) != 4:
            sys.exit(f"fdc_id {fdc} ({desc[fdc]}) misses {set(NUTRIENTS.values()) - set(v)}")
        atwater = 4 * v["protein100"] + 4 * v["carbs100"] + 9 * v["fat100"]
        if v["kcal100"] > 20 and abs(atwater - v["kcal100"]) / v["kcal100"] > 0.15:
            print(f"warning: {fdc} {name}: kcal {v['kcal100']} vs 4/4/9 {atwater:.0f}", file=sys.stderr)
        out.append({
            "id": f"sr-{fdc}",
            "name": name,
            "aliases": aliases,
            "state": {"raw": "RAW", "cooked": "COOKED"}.get(state),
            "kcal100": v["kcal100"],
            "protein100": v["protein100"],
            "carbs100": v["carbs100"],
            "fat100": v["fat100"],
            "sourceRef": f"USDA SR Legacy {fdc}: {desc[fdc]}",
        })
    out.sort(key=lambda o: o["id"])
    os.makedirs(os.path.dirname(OUT), exist_ok=True)
    doc = {
        "version": 1,
        "source": "USDA FoodData Central, SR Legacy (April 2018), public domain (CC0 1.0). fdc.nal.usda.gov",
        "foods": out,
    }
    with open(OUT, "w", encoding="utf-8") as f:
        json.dump(doc, f, ensure_ascii=False, indent=1)
        f.write("\n")
    print(f"{len(out)} foods -> {OUT}")


if __name__ == "__main__":
    if len(sys.argv) != 2:
        sys.exit(__doc__)
    main(sys.argv[1])
