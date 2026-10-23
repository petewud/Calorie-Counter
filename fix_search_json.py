from pathlib import Path

path = Path("CalorieCounter/app/src/main/java/com/example/caloriecounter/ui/FoodPicker.kt")
text = path.read_text(encoding="utf-8")
needle = "            val p = Gson().fromJson(source, SearchProduct::class.java)"
replacement = '            val normalized = source.deepCopy()\n            for (field in listOf("brands", "product_name")) {\n                val value = normalized.get(field)\n                if (value != null && !value.isJsonNull) {\n                    val label = when {\n                        value.isJsonPrimitive -> value.asString\n                        value.isJsonArray -> value.asJsonArray\n                            .filter { it.isJsonPrimitive }\n                            .joinToString(", ") { it.asString }\n                        value.isJsonObject -> {\n                            val names = value.asJsonObject\n                            val english = names.get("en")\n                            if (english != null && english.isJsonPrimitive) english.asString\n                            else names.entrySet().firstOrNull { it.value.isJsonPrimitive }?.value?.asString ?: ""\n                        }\n                        else -> ""\n                    }\n                    normalized.addProperty(field, label)\n                }\n            }\n            val p = Gson().fromJson(normalized, SearchProduct::class.java)'
if "val normalized = source.deepCopy()" in text:
    print("Search response fix already applied")
elif text.count(needle) != 1:
    raise SystemExit("Expected exactly one search parser line. No file changed.")
else:
    path.write_text(text.replace(needle, replacement), encoding="utf-8")
    print("Search response fix applied: array and multilingual labels handled")
