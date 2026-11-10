# 순정 내비게이션 에뮬레이터 검증

검증일: 2026-11-10
환경: Pleos Connect 2.1.0 / API 34, NaviHelper 3.0.5

## 확인한 항목

- 순정 내비게이션에서 `성심당 DCC점`을 목적지로 설정하고 경로 안내를 시작했다.
- 실제 `onDestinationInfo`에서 이름과 좌표(36.37556666, 127.39208333)를 수신했다.
- 안내 중 `onRouteStateInfo`가 `naviMode=ROUTE_GUIDING`, `isRouting=false`를 전달했다.
- 따라서 활성 안내 여부를 `ROUTE_GUIDING` 또는 `SIMULATION_GUIDING`으로 판정하도록 수정했다.
- 수정 빌드에서 홈 하단의 `성심당 DCC점`과 `여행 마치기` 버튼 표시를 확인했다.

## 실제 도착 이벤트: 미검증

에뮬레이터 GPS를 SDK 목적지 좌표로 이동했다. FusedLocation에는 변경된 좌표가 전달됐지만 순정 내비게이션은 남은 거리 약 137km를 유지했다. 순정 내비게이션 로그에서 다음 오류를 확인했다.

```
GpsLocationSource: Invalid NMEA message
java.lang.IllegalArgumentException: NMEA checksum mismatch: expected=47, actual=41
```

### 추가 조사: 체크섬 오류와 좌표 고정은 별개의 문제

- 에뮬레이터 콘솔(TCP 5556)의 `geo nmea`에 식별 가능한 시각을 포함한 GPRMC를 전달했다. 잘못된 체크섬 문장은 순정 앱 로그에 같은 내용으로 나타났고, 정상 체크섬 문장에서는 해당 오류가 발생하지 않았다. 정상 GGA/RMC를 2Hz로 20초 전달해도 순정 엔진 위치는 바뀌지 않았다.
- 설치된 시스템 이미지의 순정 내비게이션 APK(`3.3.2-rc5-hotfix.3`)를 읽기 전용으로 추출·분석했다. `GpsLocationSourceRepositoryImpl`의 NMEA 처리는 디버그 정보 흐름이고, 실제 위치 흐름은 `GpsManager.getLastLocation()`을 사용한다.
- `GpsManager.getLastLocation()`은 `ro.hardware.type == automotive`일 때 위도·경도를 기본 위치로 교체한다. 현재 에뮬레이터가 이 조건에 해당하며, 엔진 로그 역시 `127.094681,37.4127212`를 계속 출력했다.
- 따라서 체크섬 오류가 도착 실패의 직접 원인이라는 초기 추정은 정정한다. 정상 NMEA 입력으로도 이 좌표 고정 동작은 해제되지 않는다.
- 순정 앱에서 지도 방향을 `N`으로 전환한 뒤 지도 영역 우측 하단을 길게 누르면 개발자 메뉴 인증 창이 열린다. 실제 UI에서 `password` 입력과 `확인` 버튼을 확인했다. 인증 정보가 없어 내부 모의 주행 기능 실행은 확인하지 못했다. 비밀번호 추측·인증 우회, 순정 APK 수정은 하지 않았다.

실제 `onDestinationArrived` 콜백은 아직 확인되지 않았다. 도착 화면의 가짜 데이터 기반 Compose 테스트는 실제 순정 이벤트 검증을 대신하지 않는다.

후속 검증에는 제공사가 지원하는 모의 주행 진입 방법 또는 개발자 메뉴 인증, 혹은 GPS 좌표 고정이 없는 검증 이미지가 필요하다. 해당 환경에서 순정 엔진의 최종 도착 이벤트 → TripArchive 도착 화면 → 계속하기 및 여행 종료를 확인해야 한다. 모의 주행이 SDK 도착 콜백을 발행하는지도 검증 대상이다.

앱 구현은 이번 조사에서 변경하지 않았으며 커밋·푸시하지 않았다.

## 공식 문서 조사 (2026-11-10)

공개 공식 문서에서 개발자 메뉴 진입·인증 방법이나 순정 내비게이션 모의 주행 시작 절차는 찾지 못했다. 이는 기능이 없다는 단정이 아니라, 확인한 공개 문서 범위의 결과다.

- [Virtual Console 셋업](https://document.pleos.ai/en/docs/connect/guide/getting-started/application-development/use-vehicle-emulator): `adb forward tcp:8088 tcp:8088` 후 `http://127.0.0.1:8088/`에 접속하는 공식 차량 상태 테스트 방법을 제공한다.
- [Virtual Console 사용 가이드](https://document.pleos.ai/docs/pleos-only/app-test-guide/virtual-console-guide): 기어, 브레이크, 누적 거리, 조향, 크루즈 목표 속도 등의 제어를 설명한다. GPS 경로 재생·내비게이션 목적지 도착을 발생시키는 절차는 나와 있지 않다. 따라서 차량 상태 시뮬레이션을 내비게이션 모의 주행 지원으로 해석하지 않는다.
- [NaviHelper 공개 메서드 목록](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/NaviHelper/navi-helper/ai.pleos.playground.navi.helper/navi-helper/entry): 경로 요청·취소·정보 조회 API는 있으나 모의 주행 시작·진행 제어 메서드는 목록에서 확인되지 않는다.
- [NaviMode](https://document.pleos.ai/en/api-reference/connect-sdk-pleos/NaviHelper/navi-helper/ai.pleos.playground.navi.constants/navi-mode/entry): `SIMULATION_GUIDING` 상수는 존재하지만, 상수 존재만으로 외부 앱의 실행 API가 제공된다고 판단할 수 없다.
- [이벤트 리스너](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/NaviHelper/event-listeners): `onDestinationArrived()`와 `onWayPointArrived()`를 구분하며 실제 전달 이벤트는 Navigation 앱 구현 시나리오에 따라 달라질 수 있다고 명시한다.
- [Emulator 릴리즈 노트](https://document.pleos.ai/docs/pleos-only/release-note/emulator-release-note)와 [SDK 릴리즈 노트](https://document.pleos.ai/docs/pleos-only/release-note/sdk-release-note): 현재 사용 중인 Emulator 2.1.0 / NaviHelper 3.0.5가 기재돼 있으며, 내비게이션 모의 주행 활성화 절차는 없다.

공식 지원에 확인할 내용(아직 전송하지 않음):

> Pleos Connect Emulator 2.1.0(API 34, arm64), 순정 내비게이션 3.3.2-rc5-hotfix.3, NaviHelper 3.0.5에서 최종 목적지의 onDestinationArrived를 검증하려 합니다. 목적지 조회는 정상이나 GPS 주입 후에도 순정 엔진 좌표가 기본 위치에 머뭅니다. 외부 앱 개발자가 사용할 수 있는 모의 주행 실행 절차 또는 테스트 이미지를 제공받을 수 있나요? 개발자 메뉴 인증이 필요한 경우 공식 이용 절차와 모의 주행 종료 시 SDK 도착 이벤트 지원 여부도 안내 부탁드립니다.

문서 조사로 새 실행 방법이 확인되지 않아 이번에는 에뮬레이터 도착 재시험을 수행하지 않았다. 실제 도착 이벤트는 계속 미검증 상태다.
