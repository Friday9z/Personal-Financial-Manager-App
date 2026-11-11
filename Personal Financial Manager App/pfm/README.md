# Personal Finance Manager (Android)

แอปจัดการการเงินส่วนบุคคล — Kotlin · Jetpack Compose · Material 3 · Room · MVVM · WorkManager · CameraX · ML Kit · BiometricPrompt  
ทำงาน Offline 100% ข้อมูลทั้งหมดอยู่ในเครื่อง

> **สถานะ:** โค้ดนี้ถูกเขียนในสภาพแวดล้อมที่ไม่มี Android SDK/อินเทอร์เน็ต จึง **ยังไม่เคยถูกคอมไพล์หรือรันบนเครื่องจริง**
> ตรรกะการเงิน (`FinanceCalc`), `ReceiptParser`, `Money` มี Unit Test ที่ตรวจด้วยมือแล้ว แต่ต้องรัน `testDebugUnitTest` จริงอีกครั้ง
> ถ้า Build ขึ้น error ให้ส่งข้อความ error กลับมา จะแก้ให้ทันที (โปรเจกต์ใหญ่ มีโอกาสพบ error คอมไพล์เล็กน้อยในรอบแรก)

## 1. วิธี Build APK

### ทางที่ 1: GitHub Actions (ไม่ต้องติดตั้งอะไรในเครื่อง)
1. สร้าง repo ใหม่บน GitHub แล้ว push โฟลเดอร์นี้ขึ้นไป (branch `main`)
2. แท็บ **Actions → Build APK** รอให้เสร็จ (ราว 5-10 นาที)
3. ดาวน์โหลด artifact `PersonalFinanceManager-apk` → ได้ `app-release.apk` (เซ็นด้วย debug key ติดตั้งได้ทันที)

### ทางที่ 2: Android Studio
1. ติดตั้ง Android Studio (Koala+) แล้ว **Open** โฟลเดอร์นี้ → รอ Gradle Sync
2. เมนู **Build → Build Bundle(s)/APK(s) → Build APK(s)**
3. ไฟล์อยู่ที่ `app/build/outputs/apk/release/`  
   (หรือสั่ง `gradle :app:assembleRelease` ถ้ามี Gradle 8.9 + JDK 17 + Android SDK 34)

> ถ้าจะเผยแพร่จริง ให้สร้าง keystore ของตัวเองแทน debug key ใน `app/build.gradle.kts`

## 2. วิธีติดตั้ง
คัดลอก APK ลงมือถือ → เปิดไฟล์ → อนุญาต "ติดตั้งจากแหล่งที่ไม่รู้จัก" → Install  
Android 8.0+ (minSdk 26)

## 3. การใช้งานครั้งแรก
- เปิดแอป → **บังคับตั้ง PIN 4-6 หลัก** ก่อนเข้าใช้งาน
- ระบบสร้างหมวดหมู่เริ่มต้นและบัญชี "เงินสด" ให้ → เพิ่มบัญชีธนาคารที่ Settings → จัดการบัญชี
- กดไอคอนบนหน้า Home เพื่อ Quick Add หรือปุ่ม + กลางล่างสำหรับ Full Mode

## 4. ตั้ง PIN และ Biometrics
Settings → **PIN / Biometrics / ล็อกอัตโนมัติ**
- เปลี่ยน PIN, เปิด/ปิดลายนิ้วมือ-ใบหน้า (ใช้ BiometricPrompt ของระบบ; ต้องตั้งค่า Biometrics ในเครื่องก่อน), ล็อกเมื่อหน้าจอดับ, ล็อกอัตโนมัติ (ทันที/30 วิ/1/5 นาที)
- PIN เก็บเป็นแฮช PBKDF2-HMAC-SHA256 + salt ใน EncryptedSharedPreferences (Android Keystore) ไม่เก็บ Plain Text
- กรอกผิด 5 ครั้ง → ล็อกชั่วคราว 30 วิ และเพิ่มเป็นทวีคูณ (สูงสุด 1 ชม.)
- **ลืม PIN:** แอป Offline ไม่มีเซิร์ฟเวอร์กู้ → หน้า Lock → "ลืม PIN?" จะ **ลบข้อมูลทั้งหมด** แล้วตั้ง PIN ใหม่ (กู้คืนได้ถ้ามีไฟล์ Backup)
- หน้าจอถูกตั้ง FLAG_SECURE (ซ่อนใน Recent apps / กันแคปหน้าจอ)

## 5. ระบบแจ้งเตือน
Settings → **การแจ้งเตือน** เปิด/ปิดได้แต่ละชนิด: สรุปรายสัปดาห์ (จันทร์ 09:00), สรุปรายเดือน (วันสุดท้าย 20:00), งบประมาณ, บิล, บัตรเครดิต, หนี้, รายการประจำ, เป้าหมาย, งวดผ่อน  
ปรับเวลา weekly/monthly ได้ (รูปแบบ HH:mm) — ใช้ WorkManager (ตรวจทุก 15 นาที / ทุกชั่วโมง) จึงอาจคลาดเคลื่อนเล็กน้อยตามโหมดประหยัดแบต  
Android 13+ ต้องอนุญาตการแจ้งเตือนเมื่อแอปถาม  
แจ้งเตือนงบ: ไม่ซ้ำ — แจ้งครั้งเดียวต่อ (งบ × รอบเวลา × ระดับ%)

## 6. Export Excel
Settings → **Export Excel (.xlsx)**
- เลือกชนิดรายงาน (Line Items / สัปดาห์ / เดือน / ปี / หมวด / บัญชี / บัตร / หนี้ / ผ่อน / งบ), ช่วงวันที่, ตัวกรอง (ประเภท/หมวด/บัญชี/บัตร) และเลือกเฉพาะบางชีต
- ปุ่ม "สร้างไฟล์และแชร์" → Android Share Sheet; ปุ่ม "บันทึกไปยังตำแหน่งที่เลือก" → เลือกโฟลเดอร์เอง
- จำนวนเงินเป็นตัวเลขจริง (ใช้ SUM ได้) วันที่เป็นชนิดวันที่ รองรับภาษาไทย เปิดได้ทั้ง Excel และ Google Sheets
- ไฟล์ .xlsx เขียนเองด้วย ZipOutputStream (ไม่ใช้ Apache POI เพื่อลดขนาด/ปัญหา Android)

## 7. Backup / Restore
Settings → **Backup & Restore**
- สำรอง: ได้ไฟล์ `.zip` (ฐานข้อมูล + รูปใบเสร็จ) — **ไม่เข้ารหัส** เก็บให้ปลอดภัย และไม่รวม PIN
- กู้คืน: เลือกไฟล์ .zip → ยืนยัน → ข้อมูลปัจจุบันถูกแทนที่ → แอปเริ่มใหม่

## 8. สถาปัตยกรรมและกฎการคำนวณ
- เงินเก็บเป็น `Long` หน่วยสตางค์ (ไม่มีทศนิยมลอยตัว)
- **ยอดบัญชี/บัตร/หนี้ไม่ถูกเก็บซ้ำ — คำนวณจาก Transaction เสมอ** (`domain/FinanceCalc.kt`) → แก้/ลบรายการแล้วยอดตามอัตโนมัติ และตรงกับประวัติเสมอ
- Transfer / ชำระบัตร ไม่เป็นรายรับ-รายจ่าย; ชำระหนี้นับเฉพาะดอกเบี้ย+ค่าธรรมเนียมเป็นรายจ่าย; รายจ่ายบัตรเพิ่มหนี้บัตรโดยไม่ลดเงินในบัญชี
- กันซ้ำ: `uid` unique (idempotency) และ `recurringKey = "<recurringId>:<epochDay>"` unique
- รายการประจำ: วันที่ 29-31 เลื่อนเป็นวันสุดท้ายของเดือนสั้น; ผู้ใช้ลบรายการที่สร้างอัตโนมัติ → ไม่สร้างซ้ำ
- ใบเสร็จเก็บใน `filesDir/receipts` (พื้นที่ส่วนตัวของแอป)

## 9. Database Schema (Room, version 1)
ดู `app/src/main/java/com/pfm/app/data/Entities.kt` (และ `app/schemas/*.json` หลัง Build ครั้งแรก)

| ตาราง | หน้าที่ / คอลัมน์สำคัญ |
|---|---|
| accounts | name, type(BANK/CASH/SAVINGS/JAR/EWALLET/CUSTOM), bank, maskedRef, initialBalance, color, icon, note, includeInTotal, archived |
| categories | name, kind(INCOME/EXPENSE), icon, color, sortOrder |
| transactions | uid(unique), epochMillis, title, type(INCOME/EXPENSE/TRANSFER/CARD_PAYMENT/DEBT_PAYMENT/INSTALLMENT_PAYMENT), categoryId, amount, accountId, toAccountId, cardId, debtId, installmentId, principal, interest, fee, note, refNo, attachmentPath, scheduled, recurringId, recurringKey(unique) |
| credit_cards | name, bank, cardType, creditLimit, statementDay, dueDay, payAccountId, color |
| installments | name, cardId(FK), totalAmount, months, monthlyAmount, interestRatePct, startDay |
| debts | name, kind, initialAmount, interestRatePct, monthlyPayment, dueDay, termMonths, payAccountId |
| recurring | title, type, amount, categoryId, accountId, startDay, endDay, freq, interval, autoCreate, enabled |
| budgets | name, period, limitAmount, categoryId, accountId, fromDay, toDay, alertPercents |
| budget_alerts | (budgetId, periodKey, percent) PK — กันแจ้งเตือนซ้ำ |
| savings_goals / goal_entries | เป้าหมาย / รายการเพิ่ม-ถอน (กันเงิน ไม่กระทบยอดบัญชี) |
| app_settings | key/value (ธีม สกุลเงิน รูปแบบวันที่ สวิตช์แจ้งเตือน ฯลฯ) |

> ตาราง Credit Card Transactions และ Transfers ในสเปกถูกรวมไว้ใน `transactions` (แยกด้วย `type`/`cardId`/`toAccountId`) เพื่อให้ยอดทุกอย่างคำนวณจากแหล่งข้อมูลเดียว

## 10. ข้อจำกัดที่ต้องรู้
- **OCR ภาษาไทย:** ML Kit บนเครื่องไม่มีโมเดลภาษาไทย → อ่านตัวเลข/วันที่/อังกฤษได้ดี ข้อความไทยอาจไม่ถูกอ่าน (ผู้ใช้แก้ไขเองได้เสมอ และระบบไม่บันทึกอัตโนมัติ) ถ้าต้องการ OCR ไทยจริงต้องเพิ่ม Tesseract + `tha.traineddata`
- ผลการ Build/ทดสอบบนเครื่องจริง ดู `TESTING.md`
