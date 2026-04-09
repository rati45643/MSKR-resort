# 🌴 Palm Grove Resort App — Kotlin + Firebase

A full-featured Android resort management app with guest-facing features,
kitchen department dashboard, and room department dashboard.

---

## 📦 Tech Stack
- **Language**: Kotlin 100%
- **UI**: XML Layouts + Material Components 3
- **Backend**: Firebase (Auth, Firestore, Storage, FCM)
- **Auth**: Email/Password + Google Sign-In
- **Email**: JavaMail (SMTP via Gmail)
- **SMS**: Android SmsManager (issue reports to helpline)
- **Image loading**: Glide
- **Navigation**: DrawerLayout + Fragment transactions

---

## 🔧 Setup Steps

### 1. Create Firebase Project
1. Go to https://console.firebase.google.com
2. Click "Add project" → name it "PalmGroveResort"
3. Enable **Google Analytics** (optional)

### 2. Add Android App to Firebase
1. In Firebase Console → Project Settings → Add App → Android
2. Package name: `com.resort.app`
3. Download `google-services.json`
4. Place it at: `app/google-services.json`

### 3. Enable Firebase Services
In Firebase Console, enable:
- **Authentication** → Sign-in methods:
  - ✅ Email/Password
  - ✅ Google (add your SHA-1 fingerprint)
- **Firestore Database** → Create in production mode
- **Storage** (optional, for profile photos)

### 4. Get Google Web Client ID
1. Firebase Console → Project Settings → General
2. Scroll to "Your apps" → Web app section
3. Copy the "Web client ID"
4. Paste it in `app/src/main/res/values/strings.xml`:
   ```xml
   <string name="default_web_client_id">YOUR_WEB_CLIENT_ID_HERE</string>
   ```

### 5. Add SHA-1 Fingerprint for Google Sign-In
Run in your project root:
```bash
./gradlew signingReport
```
Copy the **SHA-1** from the debug section.
Firebase Console → Project Settings → Your apps → Add fingerprint → paste SHA-1.

### 6. Configure Email (for booking/order confirmations)
Edit `EmailHelper.kt`:
```kotlin
private const val SENDER_EMAIL    = "your.resort.email@gmail.com"
private const val SENDER_PASSWORD = "your_app_password_here"
```
To get Gmail App Password:
1. Go to myaccount.google.com → Security
2. Enable 2-Step Verification
3. Search "App passwords" → create one for "Mail"
4. Use the 16-character password generated

### 7. Configure Helpline Number
Edit `SmsHelper.kt`:
```kotlin
const val HELPLINE_NUMBER = "+919876543210"  // Replace with actual number
```

### 8. Set Firestore Security Rules
1. Firebase Console → Firestore → Rules tab
2. Copy contents of `firestore.rules` and paste

### 9. Build & Run
```bash
./gradlew assembleDebug
```
Or open in Android Studio and click Run ▶

---

## 👥 User Roles

| Role        | Access                                          |
|-------------|--------------------------------------------------|
| **User**    | Home, Room Booking, Kitchen Orders, Issues, Profile |
| **Kitchen** | Kitchen orders dashboard (live, filterable)      |
| **Room**    | Room bookings dashboard (live, check-in/out)     |

Role is selected at signup and stored in Firestore.

---

## 💰 Pricing Logic

| Budget   | Price/Room/Night |
|----------|-----------------|
| Low      | ₹1,500          |
| Medium   | ₹2,500          |
| High     | ₹4,000          |

- **10% discount** automatically applied when booking 2+ rooms
- All amounts in Indian Rupees (₹)
- Room includes: 1 Bedroom + 1 Hall + 1 Private Bathroom

---

## 🍽️ Menu Categories (30 items seeded)
- 🥦 Veg Main (Paneer, Dal, Dosa, Idli, Naan, Rice)
- 🍗 Non-Veg Main (Butter Chicken, Biryani, Rogan Josh, Fish, Chicken 65)
- 🍚 Biryani (Chicken + Veg)
- 🌮 Chaat (Pani Puri, Bhel Puri, Sev Puri, Aloo Tikki, Dahi Vada)
- 🍦 Ice Cream / Desserts (Vanilla, Chocolate, Kulfi, Gulab Jamun)
- 🥤 Juices (Lime, Mango Lassi, Watermelon, Chaas, Sugarcane)

---

## ⚠️ Issue Types Reported to Helpline
- 💧 Water Problem
- ⚡ Electricity
- 🧹 Room Cleaning
- 👕 Laundry/Clothes
- ❄️ AC/Heating
- 📶 Wi-Fi Issue
- 🔧 Plumbing
- ❓ Other

Issues are sent via SMS to the resort helpline + saved in Firestore.

---

## 📁 Project Structure

```
app/src/main/java/com/resort/app/
├── activities/
│   ├── SplashActivity.kt          # Welcome screen + auth check
│   ├── LoginActivity.kt           # Email + Google login + role select
│   ├── SignUpActivity.kt          # Full signup with all fields
│   ├── ForgotPasswordActivity.kt  # Password reset via email
│   ├── MainActivity.kt            # User home with NavigationDrawer
│   ├── KitchenDashboardActivity.kt # Kitchen dept orders view
│   └── RoomDashboardActivity.kt   # Room dept bookings view
├── fragments/
│   ├── HomeFragment.kt            # Resort info, ratings, achievements
│   ├── RoomBookingFragment.kt     # Book rooms with pricing
│   ├── KitchenFragment.kt         # Browse menu + cart + order
│   ├── IssuesFragment.kt          # Report issues → SMS helpline
│   └── ProfileFragment.kt         # Edit profile + change password
├── adapters/
│   ├── FoodCategoryAdapter.kt     # RecyclerView for food items
│   └── DepartmentAdapters.kt      # Kitchen orders + room bookings
├── models/
│   └── Models.kt                  # User, RoomBooking, FoodOrder, etc.
└── utils/
    ├── FirebaseHelper.kt           # All Firebase operations
    ├── EmailHelper.kt              # SMTP email via JavaMail
    └── SmsHelper.kt                # SMS to helpline
```

---

## 🔔 First Launch Seeding
On first launch, `SplashActivity` calls:
```kotlin
FirebaseHelper.seedResortInfo()  // Populates resort_info/main
FirebaseHelper.seedFoodMenu()    // Populates food_menu with 30 items
```
Comment these out after the first successful run.

---

## 📱 Minimum Requirements
- Android 7.0+ (API 24)
- Internet connection
- SMS permission (for issue reporting)

---

## 🛠️ Dependencies Used
```gradle
// Firebase BOM 32.7.0 — Auth, Firestore, Messaging, Storage
// Google Play Services Auth 20.7.0 — Google Sign-In
// JavaMail android-mail 1.6.7 — Email sending
// Glide 4.16.0 — Image loading
// CircleImageView 3.1.0 — Profile photos
// Material Components 1.11.0 — UI components
// Navigation Component 2.7.6 — Fragment navigation
```
