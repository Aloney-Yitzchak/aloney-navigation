<div dir="rtl">

# app

פרויקט Android Studio של האפליקציה: Java + XML, ‏minSdk 26 (Android 8.0), ‏targetSdk 35. נוצר במטלה **T07**.

## מבנה החבילות

לפי תרשים המחלקות (מסמך התיכון, סעיף 4):

| חבילה | מה יש בה |
| --- | --- |
| `il.org.ay.navigation.ui` | מסכים ודיאלוגים: `SplashActivity`, `SearchActivity`… |
| `il.org.ay.navigation.logic` | ניווט, מיקום, כיוון וזיהוי מבנים |
| `il.org.ay.navigation.data` | Room, ‏Firestore וגרף הקמפוס |
| `il.org.ay.navigation.model` | מחלקות הנתונים: `CampusRoom`, `Node`, `Edge`… |

## פתיחה ובנייה

1. ב-Android Studio: ‏**File → Open** ובוחרים את התיקייה הראשית של המאגר (`aloney-navigation`). שם נמצאים `settings.gradle` ו-`gradlew`, והמודול של האפליקציה הוא התיקייה `app`.
2. מריצים על טלפון או אמולטור (Run ▶).

משורת הפקודה:

<div dir="ltr">

```bash
./gradlew assembleDebug        # בונה app/build/outputs/apk/debug/app-debug.apk
./gradlew testDebugUnitTest    # בדיקות יחידה
```

</div>

בכל Pull Request שמשנה את `app/` רצה בנייה ובדיקות יחידה ב-GitHub Actions (‏`.github/workflows/android.yml`).

## google-services.json

הקובץ של Firebase **לא נכנס למאגר** (הוא ב-`.gitignore`). מורידים אותו מ-Firebase Console → Project settings → Your apps, ושמים אותו ב-`app/google-services.json`. בלעדיו הבנייה נכשלת.

- **בלי גישה ל-Firebase** (למשל בשכפול חדש): מעתיקים את `app/google-services.ci.json` ל-`app/google-services.json`. האפליקציה תיבנה ותרוץ, אבל לא תסנכרן נתונים.
- **ב-GitHub Actions** זה קורה אוטומטית, ולכן הבדיקות לא תלויות בפרויקט האמיתי.

## סנכרון הנתונים (T08)

`CampusRepository.sync()` רץ בכל פתיחה של האפליקציה (`SplashActivity`). הוא בודק את `meta/version` ב-Firestore, ומוריד את כל הנתונים רק כשהגרסה בענן חדשה מהגרסה שבטלפון. נתונים לא שלמים נדחים, והטלפון שומר את העותק הקודם. כל שאילתה ל-Room רצה ב-Executor ברקע, והתוצאה חוזרת ל-UI thread.

**בדיקה על הטלפון (AT-09):**
1. פותחים את האפליקציה עם אינטרנט. במסך החיפוש מופיע "בטלפון: 64 חדרים (גרסה 1)".
2. עוברים למצב טיסה, סוגרים את האפליקציה לגמרי ופותחים שוב. המסך מציג את אותם 64 חדרים.

## בדיקת תמיכה ב-ARCore (סיכון R3)

לפני מטלות ה-AR בודקים שכל מכשיר בדיקה מופיע ב[רשימת המכשירים הנתמכים של ARCore](https://developers.google.com/ar/devices), ומתקינים בו את "Google Play Services for AR" מחנות Play. מכשיר שלא נתמך יעבוד במצב הגיבוי לפי מצפן (FR-11).

</div>
