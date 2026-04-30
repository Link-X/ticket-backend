#!/bin/bash
# 压测数据生成脚本
# 生成结果：1个场地模板 + 5个演出 × 3个场次 = 15场次 × 400座位 = 6000座位
# 座位和价格通过场地模板自动复制，无需逐场次创建
#
# 依赖: jq（brew install jq）
# 用法: bash docs/seed-data.sh
#       bash docs/seed-data.sh --host http://localhost:8081

set -e

ADMIN_HOST="http://localhost:8081"
ROW_COUNT=20
COL_COUNT=20
VIP_ROWS=10   # 前 N 行为 VIP 区

while [[ "$#" -gt 0 ]]; do
  case $1 in
    --host) ADMIN_HOST="$2"; shift ;;
  esac
  shift
done

if ! command -v jq &> /dev/null; then
  echo "[ERROR] 需要 jq，请先安装: brew install jq"
  exit 1
fi

echo ""
echo "================================================="
echo "  抢票系统压测数据生成"
echo "  场地模板: ${ROW_COUNT}行×${COL_COUNT}列，前${VIP_ROWS}行VIP"
echo "  目标: 5演出 × 3场次，座位由模板自动复制"
echo "  Admin: $ADMIN_HOST"
echo "================================================="

# ─── 工具函数 ────────────────────────────────────────

check_response() {
  local resp="$1" tag="$2"
  local code
  code=$(echo "$resp" | jq -r '.code // empty' 2>/dev/null)
  if [ "$code" != "200" ]; then
    echo "[ERROR] $tag 失败: $resp"
    exit 1
  fi
}

# 生成场地座位模板 JSON（roomId 占位，外部替换）
build_room_seats_json() {
  local room_id=$1
  local json='{"roomId":'$room_id',"seats":['
  local first=true
  for row in $(seq 1 $ROW_COUNT); do
    for col in $(seq 1 $COL_COUNT); do
      [ "$first" = true ] && first=false || json+=','
      local area=0
      [ $row -le $VIP_ROWS ] && area=1
      local col_str
      col_str=$(printf "%02d" $col)
      json+='{"rowNo":'$row',"colNo":'$col',"type":1,"areaId":"'$area'","seatName":"'$row'排'$col_str'座"}'
    done
  done
  json+=']}'
  echo "$json"
}

# ─── 演出数据 ─────────────────────────────────────────

SHOW_NAMES=(
  "五月天诺亚方舟世界巡回演唱会"
  "周杰伦嘉年华世界巡回演唱会"
  "德云社甲辰年相声专场"
  "李诞脱口秀全国巡演"
  "中超联赛年度总决赛"
)
SHOW_CATEGORIES=("演唱会" "演唱会" "相声" "脱口秀" "体育")
SHOW_VENUES=(
  "国家体育场鸟巢"
  "上海梅赛德斯奔驰文化中心"
  "天津大剧院"
  "北京工人体育馆"
  "广州天河体育场"
)
SHOW_DESCS=(
  "五月天最震撼的年度演唱会，超震撼舞台效果"
  "周杰伦全球巡演上海站，经典金曲全回归"
  "郭德纲、于谦领衔，年度压轴相声专场"
  "李诞全新单口喜剧专场，笑翻全场"
  "年度最强对决，见证足球荣耀时刻"
)

SESSION_NAMES=("08月场 2026-08-01" "09月场 2026-09-06" "10月场 2026-10-04")
SESSION_STARTS=("2026-08-01T19:30:00" "2026-09-06T19:30:00" "2026-10-04T19:30:00")
SESSION_ENDS=("2026-08-01T22:00:00"   "2026-09-06T22:00:00"  "2026-10-04T22:00:00")

TOTAL_SEATS=$((ROW_COUNT * COL_COUNT))

# ─── Step 1: 创建场地模板 ─────────────────────────────

echo ""
echo ">>> [1/4] 创建场地模板..."

RESP=$(curl -s -X POST "$ADMIN_HOST/api/admin/room/create" \
  -H "Content-Type: application/json" \
  -d '{
    "name":        "标准演出场地",
    "venue":       "通用",
    "rowCount":    '$ROW_COUNT',
    "colCount":    '$COL_COUNT',
    "description": "压测用标准场地，'$ROW_COUNT'行×'$COL_COUNT'列，前'$VIP_ROWS'行VIP"
  }')
check_response "$RESP" "创建场地"
ROOM_ID=$(echo "$RESP" | jq -r '.data.id')
echo "  ✓ 场地已创建 ID=$ROOM_ID"

# 保存座位模板
SEATS_JSON=$(build_room_seats_json "$ROOM_ID")
RESP=$(curl -s -X POST "$ADMIN_HOST/api/admin/room/seat/batch" \
  -H "Content-Type: application/json" \
  -d "$SEATS_JSON")
check_response "$RESP" "保存座位模板"
echo "  ✓ 座位模板已保存 (${ROW_COUNT}行×${COL_COUNT}列=${TOTAL_SEATS}个)"

# 保存默认价格区域
RESP=$(curl -s -X POST "$ADMIN_HOST/api/admin/room/area/save" \
  -H "Content-Type: application/json" \
  -d '{
    "roomId": '$ROOM_ID',
    "areas": [
      {"areaId":"0","defaultPrice":380.00,"defaultOriginPrice":580.00},
      {"areaId":"1","defaultPrice":880.00,"defaultOriginPrice":1280.00}
    ]
  }')
check_response "$RESP" "保存价格区域"
echo "  ✓ 默认价格已保存 (普通区¥380 | VIP区¥880)"

# ─── Step 2: 创建演出 ─────────────────────────────────

echo ""
echo ">>> [2/4] 创建演出..."
SHOW_IDS=()
for i in "${!SHOW_NAMES[@]}"; do
  RESP=$(curl -s -X POST "$ADMIN_HOST/api/admin/show/create" \
    -H "Content-Type: application/json" \
    -d '{
      "name":        "'"${SHOW_NAMES[$i]}"'",
      "description": "'"${SHOW_DESCS[$i]}"'",
      "category":    "'"${SHOW_CATEGORIES[$i]}"'",
      "venue":       "'"${SHOW_VENUES[$i]}"'",
      "posterUrl":   "https://example.com/poster'$((i+1))'.jpg"
    }')
  check_response "$RESP" "创建演出"
  SID=$(echo "$RESP" | jq -r '.data.id')
  SHOW_IDS+=("$SID")
  printf "  ✓ [%d] %-30s ID=%s\n" $((i+1)) "${SHOW_NAMES[$i]}" "$SID"
done

# ─── Step 3: 创建场次（带 roomId，自动复制座位+价格） ──

TOTAL_SESSIONS=0
echo ""
echo ">>> [3/4] 创建场次（roomId=$ROOM_ID，座位和价格自动复制）..."

SESSION_IDS=()
for show_idx in "${!SHOW_IDS[@]}"; do
  SHOW_ID="${SHOW_IDS[$show_idx]}"
  echo ""
  echo "  演出 「${SHOW_NAMES[$show_idx]}」(ID=$SHOW_ID)"

  for sess_idx in "${!SESSION_NAMES[@]}"; do
    RESP=$(curl -s -X POST "$ADMIN_HOST/api/admin/session/create" \
      -H "Content-Type: application/json" \
      -d '{
        "showId":       '$SHOW_ID',
        "roomId":       '$ROOM_ID',
        "name":         "'"${SESSION_NAMES[$sess_idx]}"'",
        "startTime":    "'"${SESSION_STARTS[$sess_idx]}"'",
        "endTime":      "'"${SESSION_ENDS[$sess_idx]}"'",
        "totalSeats":   '$TOTAL_SEATS',
        "limitPerUser": 4,
        "rowCount":     '$ROW_COUNT',
        "colCount":     '$COL_COUNT'
      }')
    check_response "$RESP" "创建场次"
    SESSION_ID=$(echo "$RESP" | jq -r '.data.id')
    SESSION_IDS+=("$SESSION_ID")
    printf "    ✓ 场次 %-22s ID=%s (座位+价格已自动复制)\n" "${SESSION_NAMES[$sess_idx]}" "$SESSION_ID"
    TOTAL_SESSIONS=$((TOTAL_SESSIONS + 1))
  done
done

# ─── Step 4: 批量发布 + 预热 ──────────────────────────

echo ""
echo ">>> [4/4] 发布并预热 Redis 库存..."
for SESSION_ID in "${SESSION_IDS[@]}"; do
  RESP=$(curl -s -X PUT "$ADMIN_HOST/api/admin/session/$SESSION_ID/publish")
  check_response "$RESP" "发布场次 $SESSION_ID"

  RESP=$(curl -s -X POST "$ADMIN_HOST/api/admin/seat/warmup/$SESSION_ID")
  check_response "$RESP" "预热场次 $SESSION_ID"

  echo "  ✓ 场次 ID=$SESSION_ID 已发布并预热"
done

echo ""
echo "================================================="
echo "  生成完成"
printf "  场地模板: ID=%s (%d行×%d列)\n" "$ROOM_ID" "$ROW_COUNT" "$COL_COUNT"
printf "  演出数量: %d\n" "${#SHOW_IDS[@]}"
printf "  场次数量: %d\n" "$TOTAL_SESSIONS"
printf "  总座位数: %d\n" "$((TOTAL_SESSIONS * TOTAL_SEATS))"
echo "================================================="
