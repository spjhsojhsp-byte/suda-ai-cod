# Suda ai Code

แอป Android ที่ให้ AI เขียนโค้ด C / C++ / C# / Rust / Java / Kotlin / Python / JavaScript / Go
แล้วคัดลอก หรือพุชเป็นไฟล์ขึ้น GitHub repo ได้จากในแอป

## ใช้งาน
1. เปิดแอป กด **ตั้งค่า** ใส่ Anthropic API key (และชื่อโมเดลถ้าต้องการเปลี่ยน)
2. ถ้าจะพุช ใส่ GitHub token (สิทธิ์ Contents: Read and write), repo แบบ `เจ้าของ/ชื่อ` และบรานช์
3. เลือกภาษา พิมพ์สิ่งที่ต้องการ กด **สร้างโค้ด** แล้วกด **คัดลอก** หรือ **พุชขึ้น GitHub**

คีย์ที่ใส่เก็บไว้ในที่เก็บส่วนตัวของแอปบนเครื่องเท่านั้น

## Build
GitHub Actions (`.github/workflows/build.yml`) สร้าง APK ทุกครั้งที่พุช และแนบใน Release เมื่อพุชแท็ก `v*`
ภายในเครื่อง: JDK 17, Android SDK 34, Gradle 8.7+ แล้วรัน `gradle assembleRelease`
