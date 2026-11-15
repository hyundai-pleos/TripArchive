# 가치카카

_함께 탄 사람과 다녀온 장소를 캐릭터와 지도로 남기는 차량용 여행 기록 앱_

## 실행 방법

### APK 설치 및 실행

1. 저장소의 [apk 폴더](./apk/)에서 APK를 내려받습니다. Git으로 내려받는 경우 Git LFS가 필요합니다.
2. Pleos Connect 에뮬레이터를 실행하고 ADB 연결을 확인합니다.

   ```sh
   adb devices
   ```

3. 저장소 루트에서 다음 명령으로 APK를 설치합니다.

   ```sh
   adb install -r "apk/가치가카_가치가카_release.apk"
   ```

4. 에뮬레이터의 앱 목록에서 **TripArchive**를 실행합니다.
5. 닉네임과 관계(아이·연인·부모님)를 설정하고 프로필을 선택합니다.
6. 순정 내비게이션에서 목적지를 설정하고 경로 안내를 시작한 뒤 앱에서 목적지와 캐릭터를 확인합니다.

### 소스에서 실행

Android Studio에서 프로젝트를 열고 아래 설정을 완료한 뒤 Gradle Sync를 실행합니다. 에뮬레이터가 연결된 상태에서 다음 명령으로 빌드·설치합니다.

```sh
./gradlew :app:assembleDebug :app:installDebug
```

## 테스트 환경 및 필요한 설정

| 항목                             | 환경                                                    |
|--------------------------------|-------------------------------------------------------|
| Java                           | JDK 21                                                |
| Gradle / Android Gradle Plugin | 9.5.0 / 9.3.1                                         |
| Android SDK                    | Platform 37, Build Tools 37.0.0, Platform Tools / ADB |
| 앱 SDK 설정                       | `minSdk` 28 / `targetSdk` 36 / `compileSdk` 37        |
| 연동 SDK                         | Pleos NaviHelper 3.0.5 / Vehicle 2.3.2                |

소스에서 빌드할 때 프로젝트 루트의 `local.properties`에 SDK 경로와 Kakao REST API 키를 설정합니다.

```properties
sdk.dir=/absolute/path/to/Android/sdk
KAKAO_REST_API_KEY=your_kakao_rest_api_key
```

- 최초 빌드에는 Gradle과 의존성을 내려받을 네트워크 연결이 필요합니다.
- 실제 목적지의 장소 유형 조회에는 Kakao REST API 키와 인터넷 연결이 필요합니다. 키를 변경한 뒤에는 앱을 다시 빌드해 설치합니다.

## 개발에 사용한 Pleos Connect 에뮬레이터 버전과 화면 규격

| 항목                  | 버전·규격                       |
|---------------------|-----------------------------|
| Pleos Connect 에뮬레이터 | **2.1.0**                   |
| OS / 아키텍처           | Android 14 (API 34) / ARM64 |
| 전체 디스플레이            | **2560 × 1440 px**          |
| 2분할 앱 영역            | **1682 × 1210 px**          |
| 3분할 앱 영역            | **841 × 1210 px**           |

## APK 파일 위치 또는 GitHub Release 링크

**[APK 다운로드 — 가치가카_가치가카_release.apk](./apk/가치가카_가치가카_release.apk)**

- 저장 위치: 저장소 루트의 `apk/가치가카_가치가카_release.apk`
