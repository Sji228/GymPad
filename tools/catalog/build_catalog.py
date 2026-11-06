#!/usr/bin/env python3
"""Builds the food catalog bundled with GymPad.

Two sources go into one read-only SQLite file:
  * Products sold in Poland, from the Open Food Facts database (ODbL).
  * Basic unpackaged foods listed in basic_foods.tsv, with values from USDA FoodData Central
    SR Legacy (public domain) and names in English and Polish.

Usage:
  curl -LO https://static.openfoodfacts.org/data/en.openfoodfacts.org.products.csv.gz      (about 1.5 GB)
  curl -LO https://fdc.nal.usda.gov/fdc-datasets/FoodData_Central_sr_legacy_food_csv_2018-04.zip  (unzip it)
  python3 -m venv venv && venv/bin/pip install duckdb
  venv/bin/python build_catalog.py --off en.openfoodfacts.org.products.csv.gz \
      --usda FoodData_Central_sr_legacy_food_csv_2018-04

It writes food_catalog.db and food_catalog.version into the app's resources. The version is the
build date; the app copies the catalog to storage again whenever it changes.
"""

import argparse
import csv
import datetime
import re
import sqlite3
import sys
import unicodedata
from pathlib import Path

import duckdb

HERE = Path(__file__).resolve().parent
OUT_DIR = HERE.parents[1] / "shared/src/commonMain/composeResources/files"
USDA_SUFFIX = " (Includes foods for USDA's Food Distribution Program)"


def search_key(text):
    """Must match searchKey() in Nutrition.kt."""
    text = unicodedata.normalize("NFD", text.lower())
    text = "".join(c for c in text if unicodedata.category(c) != "Mn").replace("ł", "l")
    return re.sub(r"[^a-z0-9]+", " ", text).strip()


def products(off_csv):
    # The export is tab-separated without quoting, and a few rows are malformed; those are skipped.
    con = duckdb.connect()
    return con.execute(f"""
        SELECT code,
               trim(coalesce(nullif(trim(product_name), ''), nullif(trim(abbreviated_product_name), ''), generic_name)),
               trim(split_part(brands, ',', 1)),
               coalesce(try_cast("energy-kcal_100g" AS DOUBLE), try_cast(energy_100g AS DOUBLE) / 4.184),
               try_cast(proteins_100g AS DOUBLE), try_cast(carbohydrates_100g AS DOUBLE), try_cast(fat_100g AS DOUBLE),
               try_cast(serving_quantity AS DOUBLE), try_cast(product_quantity AS DOUBLE),
               coalesce(try_cast(unique_scans_n AS INTEGER), 0), try_cast(completeness AS DOUBLE)
        FROM read_csv('{off_csv}', delim = '\\t', quote = '', escape = '', header = true,
                      all_varchar = true, ignore_errors = true, max_line_size = 10000000)
        -- Sold in Poland, or made there (GS1 Poland's 590 prefix) and only tagged elsewhere.
        WHERE countries_tags LIKE '%en:poland%' OR code LIKE '590%'
    """).fetchall()


def plausible(kcal, protein, carbs, fat):
    if None in (kcal, protein, carbs, fat):
        return False
    if not (0 <= kcal <= 900 and all(0 <= v <= 100 for v in (protein, carbs, fat))):
        return False
    if protein + carbs + fat > 102:
        return False
    # Labels round and leave out fibre, alcohol and polyols, so allow a wide margin.
    expected = 4 * protein + 4 * carbs + 9 * fat
    return abs(expected - kcal) <= max(40, 0.3 * kcal)


def clean_amount(value):
    return round(value, 1) if value and 1 <= value <= 5000 else None


def basic_foods(usda_dir):
    usda_dir = Path(usda_dir)
    ids = {}
    with open(usda_dir / "food.csv", newline="") as f:
        for row in csv.DictReader(f):
            ids[row["description"].removesuffix(USDA_SUFFIX)] = row["fdc_id"]
    wanted = {"1008": "kcal", "1003": "protein", "1005": "carbs", "1004": "fat"}
    values = {}
    with open(usda_dir / "food_nutrient.csv", newline="") as f:
        for row in csv.DictReader(f):
            key = wanted.get(row["nutrient_id"])
            if key:
                values.setdefault(row["fdc_id"], {})[key] = float(row["amount"])
    foods = []
    with open(HERE / "basic_foods.tsv", newline="") as f:
        for row in csv.DictReader(f, delimiter="\t"):
            fdc_id = ids.get(row["usda_description"])
            if fdc_id is None:
                sys.exit(f"Not in USDA data: {row['usda_description']}")
            v = values[fdc_id]
            foods.append((
                f"usda:{fdc_id}", row["name_en"], row["name_pl"], None,
                v["kcal"], v["protein"], v.get("carbs", 0.0), v["fat"],
                float(row["portion_g"]) if row["portion_g"] else None, None,
            ))
    return foods


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--usda", required=True, help="Unzipped USDA SR Legacy CSV folder")
    parser.add_argument("--off", required=True, help="Open Food Facts CSV export (.csv.gz)")
    args = parser.parse_args()

    out = OUT_DIR / "food_catalog.db"
    out.unlink(missing_ok=True)
    db = sqlite3.connect(out)
    db.executescript("""
        CREATE TABLE food (
          code TEXT PRIMARY KEY,
          name TEXT NOT NULL,
          name_pl TEXT,
          brand TEXT,
          kcal REAL NOT NULL,
          protein REAL NOT NULL,
          carbs REAL NOT NULL,
          fat REAL NOT NULL,
          portion_g REAL,
          package_g REAL,
          search TEXT NOT NULL,
          rank INTEGER NOT NULL
        ) WITHOUT ROWID;
    """)

    basics = basic_foods(args.usda)
    for food in basics:
        code, name, name_pl, brand, *nutrition = food
        db.execute(
            "INSERT INTO food VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
            (code, name, name_pl, brand, *(round(v, 1) if isinstance(v, float) else v for v in nutrition),
             search_key(f"{name} {name_pl}"), 1_000_000_000),
        )

    kept = 0
    seen = set()
    for code, name, brand, kcal, protein, carbs, fat, serving, package, scans, completeness in products(args.off):
        code = (code or "").strip()
        if not code.isdigit() or not (8 <= len(code) <= 14) or code in seen:
            continue
        if not name or len(name) < 2 or not plausible(kcal, protein, carbs, fat):
            continue
        seen.add(code)
        brand = brand or None
        rank = scans * 10 + int((completeness or 0) * 9)
        db.execute(
            "INSERT INTO food VALUES (?, ?, NULL, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
            (code, name[:120], brand[:60] if brand else None,
             round(kcal, 1), round(protein, 1), round(carbs, 1), round(fat, 1),
             clean_amount(serving), clean_amount(package),
             search_key(f"{name} {brand or ''}"), rank),
        )
        kept += 1

    db.commit()
    db.execute("VACUUM")
    db.close()
    version = datetime.date.today().isoformat()
    (OUT_DIR / "food_catalog.version").write_text(version + "\n")
    print(f"{len(basics)} basic foods, {kept} products, {out.stat().st_size / 1e6:.1f} MB, version {version}")


if __name__ == "__main__":
    main()
