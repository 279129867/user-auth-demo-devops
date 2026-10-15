#!/bin/bash
# 数据库备份脚本：导出 MySQL 数据，保留最近 7 天
BACKUP_DIR="./backups"
mkdir -p "$BACKUP_DIR"

TIMESTAMP=$(date +%Y%m%d_%H%M%S)
FILE="$BACKUP_DIR/user_auth_demo_$TIMESTAMP.sql"

echo "开始备份数据库..."
docker exec demo-mysql mysqldump -uroot -proot user_auth_demo > "$FILE"

if [ $? -eq 0 ]; then
    SIZE=$(du -h "$FILE" | cut -f1)
    echo "备份成功: $FILE ($SIZE)"
else
    echo "备份失败!"
    exit 1
fi

# 删除 7 天前的备份
echo "清理 7 天前的旧备份..."
find "$BACKUP_DIR" -name "*.sql" -mtime +7 -delete
echo "完成"
