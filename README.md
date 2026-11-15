# 가치카카

**함께 탄 사람과 다녀온 장소를 캐릭터와 지도로 남기는 차량용 여행 기록 앱**

가족과의 나들이, 연인과의 드라이브, 부모님과의 여행. 같은 길을 달리고 같은 장소에 도착해도, 누구와 함께했는지에 따라 다른 추억이 됩니다.

가치카카는 함께 이동하는 사람에 맞는 캐릭터와 차량의 이동 정보를 연결합니다. 출발 전에는 이번 여행의 캐릭터를 만나고, 이동 중에는 차량 상태와 목적지에 따른 변화를 경험하며, 여행 후에는 함께 들른 장소를 돌아볼 수 있습니다.

Pleos Connect 환경을 대상으로 개발하는 Android Automotive OS 앱입니다.

## 기획 배경

차량은 가까운 사람들과 시간을 보내는 공간이기도 합니다. 하지만 이동 중의 경험을 남기려면 사진을 찍거나 별도의 기록을 작성해야 하고, 운전자는 운전에 집중해야 합니다.

가치카카는 차량에서 얻을 수 있는 상태와 목적지 정보를 활용해 기록의 부담을 줄이고자 시작했습니다. 캐릭터가 함께 이동하는 동반자가 되고, 방문한 장소가 그날의 이야기를 떠올리는 단서가 되는 경험을 지향합니다.

한 번의 여행은 캐릭터의 한 장면으로, 여러 번의 여행은 점점 짙어지는 우리만의 지도로 남기는 것이 프로젝트의 목표입니다.

## 주요 경험

### 함께 타는 사람이 바뀌면, 캐릭터도 바뀝니다

프로필을 만들 때 닉네임을 직접 입력하고, 함께 이동할 사람과의 관계를 ‘아이’, ‘연인’, ‘부모님’ 중에서 선택합니다. 출발 전 사용할 프로필을 선택하면 관계에 맞는 캐릭터와 의상·액세서리가 표시되고, 여행 기록도 프로필별로 구분됩니다.

| 관계 | 캐릭터 | 남기고 싶은 이야기 |
| --- | --- | --- |
| 아이 | 아동 캐릭터 | 아이와 함께 자라가는 가족의 나들이 |
| 연인 | 성인 캐릭터 | 둘이 함께한 데이트와 드라이브 |
| 부모님 | 부모님 캐릭터 | 부모님을 모시고 다녀온 여행 |

여행을 시작할 때 의상과 액세서리를 무작위로 조합해 이번 여행의 모습을 정합니다. 여행 중에는 같은 착장을 유지하고, 여행을 마치면 다음 여행을 위한 새로운 조합을 준비합니다.

### 차의 움직임에 캐릭터가 반응합니다

캐릭터는 차량의 속도에 따라 기다리거나 걷고 달리는 모습을 보여줍니다. 화면 속 캐릭터와 실제 이동이 연결되어, 함께 여행하는 느낌을 더합니다.

차량의 기어와 주행 제한 상태도 함께 확인합니다. 주행 제한 상태에서는 프로필 편집과 화면 이동 등의 조작을 제한하며, 차량 상태를 확인할 수 없을 때도 제한 상태를 기본으로 적용합니다.

### 목적지에 어울리는 소품이 등장합니다

순정 내비게이션에 설정한 목적지를 홈 화면에서 확인할 수 있습니다. 목적지의 이름과 좌표로 장소 유형을 조회하고, 해당 장소를 표현하는 3D 소품을 캐릭터 곁에 배치합니다.

현재는 목적지가 바뀌면 소품도 교체됩니다. 앞으로는 방문한 장소의 소품을 여행 내내 누적해, 하루의 여정이 캐릭터와 배경에 담기도록 확장할 계획입니다.

### 여행이 끝나면 함께한 장소를 돌아봅니다

도착한 장소를 확인한 뒤 다음 목적지로 이동하거나 여행을 마칠 수 있습니다. 여행을 종료하면 방문 장소와 순서, 시작·종료 시각을 해당 프로필의 기록으로 저장합니다.

기록 목록에서 지난 여행을 선택하면 어떤 장소들을 어떤 순서로 방문했는지 확인할 수 있습니다. 가족과 다녀온 나들이와 연인과의 드라이브를 각각의 프로필에서 돌아볼 수 있습니다.

### 다시 찾은 곳일수록, 우리 지도가 짙어집니다

방문 지도는 함께 다녀온 지역을 한눈에 돌아보기 위한 기능입니다. 같은 지역이라도 누구와 방문했는지에 따라 별도로 기록하고, 자주 찾은 지역일수록 색이 짙어지는 지도를 지향합니다.

현재는 행정구역 경계 표시와 지도 확대·축소·이동, 방문 횟수에 따른 색상 계산을 구현했습니다. 실제 방문 기록을 지도에 연결하고 프로필별로 누적하는 기능은 후속 개발 범위입니다.

## 여행의 흐름

1. **함께할 사람 선택** — 닉네임과 관계를 설정하고 이번 여행의 프로필을 선택합니다.
2. **캐릭터와 출발** — 이번 여행의 의상과 액세서리를 확인합니다.
3. **목적지로 이동** — 내비게이션에 설정한 목적지와 어울리는 소품, 차량 움직임에 반응하는 캐릭터를 만납니다.
4. **도착 후 다음 여정 선택** — 방문한 장소를 확인하고 계속 이동하거나 여행을 마칩니다.
5. **지난 여행 돌아보기** — 프로필별 기록에서 함께 들른 장소와 방문 순서를 확인합니다.

## 차량과 연결되는 방식

가치카카는 차량 화면에서 캐릭터를 보여주는 경험에 이동 정보를 연결합니다.

- **차량 상태**: Android Car API의 속도·기어·주행 제한 정보를 캐릭터 반응과 조작 제한에 반영합니다.
- **목적지 정보**: Pleos NaviHelper에서 목적지와 경로 상태를 받습니다.
- **장소 유형**: Kakao Local API로 목적지를 분류해 소품을 표시합니다.
- **여행 기록**: 프로필과 여행 정보를 기기 내부에 저장합니다.

## 사용 기술

| 분야 | 기술 |
| :--- | :--- |
| **Language** | ![Kotlin](https://img.shields.io/badge/Kotlin-7F52FF?style=flat-square&logo=kotlin&logoColor=white) |
| **UI & Navigation** | ![Jetpack Compose](https://img.shields.io/badge/Jetpack_Compose-4285F4?style=flat-square&logo=jetpackcompose&logoColor=white) ![Navigation 3](https://img.shields.io/badge/Navigation_3-4285F4?style=flat-square) |
| **Architecture** | ![Hilt](https://img.shields.io/badge/Hilt-6750A4?style=flat-square) ![Coroutines](https://img.shields.io/badge/Coroutines-7F52FF?style=flat-square) ![Flow](https://img.shields.io/badge/Flow-7F52FF?style=flat-square) |
| **Local Storage** | ![Room](https://img.shields.io/badge/Room-3DDC84?style=flat-square&logo=android&logoColor=white) |
| **3D Graphics** | ![SceneView](https://img.shields.io/badge/SceneView-FF6F00?style=flat-square) |
| **Vehicle & Navigation** | ![Android Car API](https://img.shields.io/badge/Android_Car_API-3DDC84?style=flat-square&logo=android&logoColor=white) ![Pleos NaviHelper](https://img.shields.io/badge/Pleos_NaviHelper-172B4D?style=flat-square) |
| **Location API** | ![Kakao Local API](https://img.shields.io/badge/Kakao_Local_API-FEE500?style=flat-square&logo=kakao&logoColor=000000) |
