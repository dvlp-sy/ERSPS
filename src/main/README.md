## How to Test

1. git clone
```bash
git clone https://github.com/dvlp-sy/ERSPS.git
```

2. gradlew build
```bash
cd ERSPS
./gradlew build
```

3. docker compose up
- db가 실행되었는지 확인 후 app 실행
```bash
docker-compose up db
docker-compose up -d
```

3. execution
- 브라우저에서 localhost로 접속
```
localhost
```

4. docker compose down
```bash
docker-compose down
```