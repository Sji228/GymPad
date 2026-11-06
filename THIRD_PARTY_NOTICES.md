# Third-party notices

GymPad bundles the following third-party material. Libraries pulled in by Gradle
are listed in [`gradle/libs.versions.toml`](gradle/libs.versions.toml).

## Inter (font)

Subset to Latin and Latin Extended. Licensed under the SIL Open Font License 1.1.
The full license ships with the app:
[`inter_license.txt`](shared/src/commonMain/composeResources/files/inter_license.txt).

## Lucide (icons)

The icon shapes in [`GymIcons.kt`](shared/src/commonMain/kotlin/com/gympad/ui/GymIcons.kt)
are adapted from [Lucide](https://lucide.dev), under the ISC License:

```
ISC License

Copyright (c) 2026 Lucide Icons and Contributors

Permission to use, copy, modify, and/or distribute this software for any
purpose with or without fee is hereby granted, provided that the above
copyright notice and this permission notice appear in all copies.

THE SOFTWARE IS PROVIDED "AS IS" AND THE AUTHOR DISCLAIMS ALL WARRANTIES
WITH REGARD TO THIS SOFTWARE INCLUDING ALL IMPLIED WARRANTIES OF
MERCHANTABILITY AND FITNESS. IN NO EVENT SHALL THE AUTHOR BE LIABLE FOR
ANY SPECIAL, DIRECT, INDIRECT, OR CONSEQUENTIAL DAMAGES OR ANY DAMAGES
WHATSOEVER RESULTING FROM LOSS OF USE, DATA OR PROFITS, WHETHER IN AN
ACTION OF CONTRACT, NEGLIGENCE OR OTHER TORTIOUS ACTION, ARISING OUT OF
OR IN CONNECTION WITH THE USE OR PERFORMANCE OF THIS SOFTWARE.
```

## Open Food Facts (product data)

[`food_catalog.db`](shared/src/commonMain/composeResources/files/food_catalog.db) contains
information from [Open Food Facts](https://world.openfoodfacts.org), which is made available here
under the [Open Database License (ODbL) 1.0](https://opendatacommons.org/licenses/odbl/1-0/).
Individual contents of the database are available under the
[Database Contents License](https://opendatacommons.org/licenses/dbcl/1-0/).

The catalog is a derivative database: products sold or made in Poland, reduced to name, brand,
calories, protein, carbohydrates, fat and package sizes, with implausible values left out. It is
offered under the same ODbL terms, and [`tools/catalog`](tools/catalog) rebuilds it from the
public export.

## USDA FoodData Central (basic foods)

Values for the basic foods in [`basic_foods.tsv`](tools/catalog/basic_foods.tsv) come from the
SR Legacy release of [USDA FoodData Central](https://fdc.nal.usda.gov), which is in the public
domain (CC0 1.0). The English and Polish names are GymPad's own.

## ML Kit and CameraX (barcode scanning)

The Android app reads barcodes with Google's
[ML Kit barcode scanning](https://developers.google.com/ml-kit/vision/barcode-scanning) model,
bundled into the app and used under the [ML Kit Terms of Service](https://developers.google.com/ml-kit/terms),
through [CameraX](https://developer.android.com/media/camera/camerax) (Apache License 2.0).
ML Kit's network permissions are removed from the app manifest, so nothing is sent from the device.
