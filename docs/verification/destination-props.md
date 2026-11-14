# 목적지 분류별 3D 소품 검증

홈 화면은 순정 내비게이션 목적지에 제공된 분류값을 받아 해당 3D 소품 하나를 캐릭터 오른쪽에 표시한다. 현재 소품 파일은 `app/src/main/assets/models/destination_props/` 아래의 `mart.glb`, `school.glb`, `tourism.glb`, `restaurant.glb`, `hospital.glb`이다. 소품은 캐릭터와 같은 SceneView에 별도 모델 노드로 올리고, 캐릭터 모델 크기·modifier·카메라를 바꾸지 않는다.

카드와 도착 다이얼로그에는 동일한 분류 아이콘과 한국어 라벨을 표시한다. 분류가 없거나 알 수 없는 값이면 분류 라벨 없이 기존 목적지 깃발을 사용한다.

## 분류 입력 경계

화면은 목적지 이름을 보고 분류하지 않는다. `DestinationState.category`와 `arrivedCategory`만 사용한다. 현재 `PleosDestinationSource`는 목적지 이름만 받기 때문에 `category = null`을 보낸다. #23 분류 제공자는 이 경계에서 분류를 전달해야 한다. 목적지 이름 규칙이나 임의의 문자열 목록을 UI에 추가하지 않는다.

새 경로의 분류는 `destinationChanged(name, category)`로 전달한다. 경로 취소는 활성 이름과 분류를 함께 지운다. 도착 이벤트는 활성 목적지 이름과 일치하는 분류만 `arrivedCategory`로 옮기며, 도착 확인 시 도착 이름과 분류를 함께 지운다.

## 데모 앱 실행

```sh
./gradlew --offline :app:assembleDemo :app:installDemo
```

APK: `app/build/outputs/apk/demo/app-demo.apk`  
패키지: `com.triparchive.demo`. 데모 앱에서 프로필을 선택해 홈을 연다. 홈 왼쪽 위의 노란 `DEMO · 목적지 입력` 패널에서 다섯 분류와 `분류 없음`, `경로 취소`, `도착 재현`, `SDK 상태로 복귀`를 사용할 수 있다. 목적지를 바꾸면 분류 아이콘·라벨과 3D 소품도 함께 바뀐다. 분류 없음에서는 깃발 표시와 소품 숨김을 확인한다. 도착 재현은 현재 목적지의 분류를 도착 다이얼로그에 유지한다.

## ADB 입력

데모 화면을 연 상태에서 다음 브로드캐스트를 보낸다. 수신 action은 `com.triparchive.demo.DESTINATION_INPUT`이다. 이 receiver는 `src/demo`에만 포함된다.

```sh
# 마트 목적지 설정
adb shell am broadcast -a com.triparchive.demo.DESTINATION_INPUT -p com.triparchive.demo --es name "이마트 강릉점" --es category mart --ez routing true

# 관광 목적지로 교체
adb shell am broadcast -a com.triparchive.demo.DESTINATION_INPUT -p com.triparchive.demo --es name "경포대" --es category tourism --ez routing true

# 분류 없음 확인
adb shell am broadcast -a com.triparchive.demo.DESTINATION_INPUT -p com.triparchive.demo --es name "성심당 DCC점" --es category unknown --ez routing true

# 현재 경로 도착 처리. name/category/routing 없이 보내면 현재 데모 목적지를 사용한다.
adb shell am broadcast -a com.triparchive.demo.DESTINATION_INPUT -p com.triparchive.demo --ez arrived true

# 현재 경로 취소. 활성 목적지 이름과 분류를 지운다.
adb shell am broadcast -a com.triparchive.demo.DESTINATION_INPUT -p com.triparchive.demo --ez routing false

# 데모 입력을 해제하고 Pleos 목적지 상태로 복귀
adb shell am broadcast -a com.triparchive.demo.DESTINATION_INPUT -p com.triparchive.demo --ez reset true
```

`category`에 허용된 소문자 값은 `mart`, `school`, `tourism`, `restaurant`, `hospital`이다. `unknown` 또는 다른 값은 미분류로 처리한다. `arrived=true`는 활성 경로가 있어야 한다. `reset=true`는 다른 extra보다 우선한다. `routing=false`는 현재 데모 경로를 취소한다.

## 검증 명령

```sh
JAVA_HOME=/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home ./gradlew --offline :app:ktlintCheck :app:testDebugUnitTest :app:assembleDebug :app:assembleDemo
```

`DestinationStateTest`는 분류 보존, 취소, 목적지 교체, 미분류 목적지, 이름이 일치하지 않는 도착을 확인한다. `DestinationHomeTest`는 도착 분류가 없을 때 새 활성 목적지의 분류를 도착 화면에 섞지 않는지 확인한다. ADB 입력과 Pleos에서 실제 분류를 공급하는 통합 검증은 데모 receiver 입력과 별도로 확인한다.

## 실행 결과 (2026-11-14)

Pleos Connect 2.1.0 / API 34, 사용자 10의 2분할 화면에서 아이 프로필로 확인했다. 데모 입력으로 마트 → 학교 → 관광 → 음식점 → 병원으로 바꿨을 때 각각 바구니, 책·연필, 카메라, 접시·클로슈, 의료 가방으로 교체됐다. 미분류와 경로 취소에서는 소품이 사라졌고, 병원 도착 다이얼로그에는 병원 분류가 유지됐다. 캐릭터 카메라·모델 스케일은 변경하지 않았고, 녹화에서 목적지 전환 중 같은 구도와 크기를 유지했다.

로컬 영상: `.state/props26/destination-props-pleos.mp4` (Git 제외). 데모 패널이 보이는 입력 재현 영상이며 실제 내비게이션 분류 연동 영상은 아니다. 실제 분류 제공은 #23 연결 후 별도 확인해야 한다. 성인·부모님 분할 화면 검증은 아래 후속 결과에 기록했다.

- `ktlintCheck`, `testDebugUnitTest`, `assembleDebug`, `assembleDemo` 통과.
- `assembleDebugAndroidTest`, `compileReleaseKotlin` 통과.
- Pleos에서 `arrivalCategoryDoesNotFallBackToTheNextActiveDestination` 계측 테스트 1개 통과 (1.067초).
- 독립 코드 리뷰에서 동일 목적지 도착 재현 시 확인 상태가 남는 문제를 수정하고 재리뷰했다.
- 5개 GLB 총 426,172바이트. 텍스처·스킨·애니메이션 없이 개별 10,000삼각형 미만이다.

## 커밋 구분

1. 목적지 분류·도착 상태 계약과 단위 테스트.
2. 경량 GLB 5종과 재생성 스크립트.
3. SceneView 소품 슬롯, 홈·도착 표시, 데모 입력 및 계측 테스트.
4. 검증 절차와 실제 실행 결과 기록.


## 분할 화면 렌더링 수정과 후속 검증 (2026-11-14)

수정 전에는 2→3분할 후 목적지 라벨만 바뀌고 3D 소품이 이전 상태로 멈췄다. 프로필 화면에서 먼저 3분할로 바꾼 뒤 홈에 진입하면 캐릭터가 표시되지 않는 경우도 있었다. 이때 로그에서 `EGL_BAD_MATCH`와 Filament의 `makeCurrent` 실패가 반복됐다. OpenGL 엔진 생성 방식만 바꾸는 실험은 증상을 해결하지 못해 되돌렸다.

분할 크기가 바뀌면 SceneView의 출력 표면과 렌더러를 다시 만들되, Filament Scene·모델 인스턴스·ModelNode·카메라·View는 유지한다. 외부 SceneNodeManager가 노드를 관리하고 새 렌더 루프에서 루트 노드의 `onFrame`을 호출한다. SceneView 전체에 크기 키만 넣는 중간안은 모델 로딩 중 해제를 유발해 `Texture is not created yet` 크래시가 발생했으므로 채택하지 않았다. 최종안은 모델 로딩 및 노드 수명을 크기 키 바깥으로 분리한다.

카메라 z=2.05, 캐릭터 scaleToUnits=1, 기존 홈 캐릭터 영역, 소품 scaleToUnits=0.22 및 위치 (0.5, -0.35, 0)는 유지했다. 데모 패널은 오른쪽 중앙에서 왼쪽 위(기존 화면 배율 기준 start 16dp/top 144dp)로 이동했다.

최종 데모 APK를 Pleos Connect 2.1.0 / API 34에 설치해 다음을 확인했다.

| 프로필 | 2분할 (1682×1210) | 3분할 (841×1210) |
| --- | --- | --- |
| 성인 | 다섯 소품 전환 및 미분류 숨김 정상 | 프로필에서 첫 진입, 다섯 소품 전환 및 미분류 숨김 정상 |
| 부모님 | 다섯 소품 전환 및 미분류 숨김 정상 | 프로필에서 첫 진입, 다섯 소품 전환 및 미분류 숨김 정상 |

성인·부모님 각각 2↔3분할을 세 차례 반복하고 학교·병원 소품을 번갈아 표시했으며, 최종 로그에서 `EGL_BAD_MATCH`, `Fatal signal`, `PreconditionPanic`은 검출되지 않았다. 총 20개 분류 표시 조합에서 소품 가림·캐릭터와의 겹침을 확인하지 못했고, 동일 화면 크기에서 기존 캐릭터 구도·크기를 유지했다. 캡처는 `.state/props26/split-fix/run2/qa/final-{adult,parent}-{2,3}-*.png`에 로컬 보관하며 Git에는 포함하지 않는다.

`ktlintCheck`, `testDebugUnitTest`, `assembleDemo`, `assembleDebug`, `git diff --check`가 통과했다. 실제 목적지 분류 공급은 여전히 #23 연동 후 검증해야 한다. 이번 결과는 데모 입력으로 렌더링과 분할 전환을 확인한 것이며, 모든 착장 조합 또는 실제 차량에서의 검증을 뜻하지 않는다.

별도 기존 문제로 프로필 관계를 편집한 뒤 화면 복귀 시 `Texture is not created yet` 크래시가 수정 전에도 관찰됐다. 분할 전환 수정으로 해당 편집 경로까지 해결됐다고 보지는 않으며, 프로필 편집 후 모델 교체 수명은 별도 조사 대상이다.
