<div dir="rtl">

# campus-data — נתוני הקמפוס

ההסבר המלא, כולל איך ממפים בשטח: [נתוני הקמפוס בוויקי](https://github.com/Aloney-Yitzchak/aloney-navigation/wiki/נתוני-הקמפוס).

| קובץ / תיקייה | מה | מטלה |
| --- | --- | --- |
| `nodes.csv`, `edges.csv` | צמתי שבילים וכניסות, וקטעי השבילים ביניהם | T05 |
| `rooms.csv` | שיבוץ החדרים לשנה הנוכחית (מתרשים תשפ"ז) | T05, T28 |
| `campus.py` | קריאת הקבצים, נוסחת המרחק ובדיקות הגרף — משותף לכל הסקריפטים | T05 |
| `validate.py` | בדיקת הנתונים, ומילוי `length_m` עם `--write` | T05 |
| `gpx_to_nodes.py` | הוספת הנקודות מהסיור (GPX או CSV) ל-`nodes.csv` | T05 |
| `preview.html` | מפה לבדיקה ועריכה של הנקודות והשבילים | T05 |
| `tests/` | בדיקות יחידה: `python -m unittest -v` | T05 |
| `plans/<שנה>/plan.png` | תרשים בית הספר של אותה שנה, מתפרסם ב-GitHub Pages | T28 |
| `upload_to_firestore.py` | העלאת הנתונים ל-Firestore | T06 |

הסקריפטים משתמשים רק בספרייה הסטנדרטית של Python ‏(3.10 ומעלה).

</div>
