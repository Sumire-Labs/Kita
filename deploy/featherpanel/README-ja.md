# Kita専用Spell導入キット

KitaのJava 25版（Sumire-Labs/Kita）用です。稼働中のBOT・DB・Lavalinkは自動変更しません。
BOT本体のみFeatherWingsで管理し、MariaDBとLavalinkは既存のものを利用します。
Ubuntu/Debian VMを作る必要はありません。Linux amd64を対象とします。

## 更新の仕組み

- GitHub Releaseにある `kita-<version>.zip` と `SHA256SUMS` を使います。
- 初回の `KITA_VERSION=latest` は、その時点の安定版に固定されます。
- 更新するときは、パネルの起動設定で `KITA_VERSION` を新しいリリースの正確なタグ名に変更し、再起動します。
- 設定・データ・プリセットは更新対象から分離しています。
- ダウンロード、チェックサム、展開の検証に失敗した場合は起動を中止します。
- 一度取得したタグはローカルに保存され、再起動のたびにはダウンロードしません。
- `latest` を指定したまま再起動しても、自動で新バージョンには切り替えません。
- 過去に取得したタグに戻せます。ただし、DBのマイグレーションは自動で巻き戻りません。

## 1. ランタイムイメージを用意

このフォルダーのうち `publish-runtime.yml` 以外を、Kitaリポジトリの
`deploy/featherpanel/` に配置してください。
`publish-runtime.yml` は `.github/workflows/publish-runtime.yml` に配置します。
コミット・プッシュ後、GitHub Actionsからこのワークフローを手動実行します。
公開先は `ghcr.io/sumire-labs/kita-featherpanel:java25` です。
GHCRのパッケージは、ノードから取得できるようPublicにするか、ノードのレジストリ認証を設定します。
このワークフローはBOTのリリースとは別で、ランタイム更新が必要なときだけ実行します。

Ubuntuでイメージをローカル確認する場合：

```bash
docker build -t ghcr.io/sumire-labs/kita-featherpanel:java25 deploy/featherpanel
```

Spellのインストーラーも同じイメージを使用するため、サーバー作成前に取得可能にしてください。

## 2. Kita本体のReleaseを公開

既存の `.github/workflows/release.yml` を利用します。
テストを通したバージョンタグをプッシュして、次のファイルが公開されることを確認します。

- `kita-<version>.zip`（Gradleアプリケーション配布物）
- `SHA256SUMS`
- `kita-audio-<version>.jar`（Lavalink用。BOTの更新スクリプトでは変更しません）

このキット作成時点では公開済みReleaseを確認できなかったため、初回起動前に公開が必要です。
自動生成される「Source code (zip)」では動きません。

## 3. 稼働中の構成を確認

現在動いているKitaと、リポジトリのJava 25版が同じ世代か確認してください。
新旧でDBスキーマが違う場合は、まずDBの複製で互換性を検証します。

既存の `database` / `lavalink` は、Compose内部だけで解決できる名前の場合があります。
Wingsの別ネットワークから使うには、到達可能な接続先を用意する必要があります。
実際のポート・ネットワークを確認してから設定し、DB・Lavalinkはルーターで公開しないでください。

設定例（このポートが実際に用意されている場合に限ります）：

- `JDBC_URL=jdbc:mariadb://192.168.0.4:3307/kita`
- `LAVALINK_URI=http://192.168.0.4:2334`

既存ComposeのDBボリューム名を変えたり、新しい空のDBに置き換えたりしないでください。

## 4. Spellを取り込む

FeatherPanelの「管理 → Spell」で `spell-kita.json` をインポートします。
必要なら、先にBOT用のRealmを作成して選択します。
テスト用サーバーを作成し、管理者が以下を入力します。

- `KITA_VERSION`：公開した正確なリリースタグ（初回のみ `latest` も使用可）
- `DISCORD_TOKEN`
- `JDBC_URL`、`MARIADB_USER`、`MARIADB_PASSWORD`
- `LAVALINK_URI`、`LAVALINK_PASSWORD`
- `DEEPL_KEY`（必要な場合のみ）

CPU・メモリは既存BOTの使用量を基に設定します。現行ComposeのBOTメモリ上限は3GiBです。
BOTはDiscordへ発信するため、BOT用の受信ポートをルーターで開放する必要はありません。
パネルが必須とする割り当てには、未使用のものを選びます。

## 5. データ移行と切り替え

1. DBのダンプと既存Kitaの設定・data・presetsをバックアップする。
2. 旧BOTを停止する。DBとLavalinkは停止しない。
3. `data/`、`presets/`、必要な設定をSFTPで新しいサーバー領域へコピーする。
4. `config/kita.yml` は同梱例と現行設定を比較し、古い固定パスやCompose内部名を調整する。
5. 新BOTを起動し、Discord接続、コマンド、DBの既存データ、音楽再生を確認する。

同じDiscordトークンの旧BOTと新BOTを同時に接続しないでください。
接続情報はパネルへ直接入力し、チャットやGitには貼り付けません。

## 6. 日常の更新

1. GitHubにテスト済みReleaseを公開する。
2. DBと `config/`、`data/`、`presets/` をバックアップする。
3. パネルの `KITA_VERSION` をそのReleaseのタグ名に変更し、保存して再起動する。
4. ログ、Discord接続、音楽再生を確認する。

`.kita/current.json` で実際に選択されたタグを確認できます。
更新後も `config/kita.yml` を上書きしないため、新しい設定項目は必要に応じて手動で反映します。
アプリ本体は `.kita/releases/` に保存されます。不要な旧版の掃除は自動では行いません。

音声処理側の変更を含むリリースでは、対応する `kita-audio-<version>.jar` とLavalink側の更新も必要です。
現行Lavalinkイメージにはそのプラグインが組み込まれているため、既存Compose側で更新します。
DBスキーマや音声プロトコルが変わる更新は、BOTだけの差し替えでは完結しません。

## 検証範囲

更新スクリプトは疑似Releaseを使った13項目のテストに合格しています（整合性検証、データ保持、キャッシュ、切り戻し、危険なZIPの拒否など）。
Linux Dockerでイメージのビルドに成功し、Java 25、FFmpeg、yt-dlpの実行とシェル構文を確認しました。
実際のDiscordログイン、現在稼働中のDBとの互換性、FeatherPanelへのインポートと起動は導入時に確認します。
