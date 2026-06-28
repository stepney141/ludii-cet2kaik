このプロジェクトは、Ludii General Game Systemを用いて、机戦(cet2kaik, cerke, cetkaik)という創作ボードゲームのルールをLudii GDL(ludファイル形式)で表現し、Ludiiの上で机戦を実装することを目的としている。現在のlud実装は @Cetkaik.lud にある。作業のときは以下の指示を守ること。

- Ludiiや机戦のソースコードへのリンクは @README.md に載っているので、git clone して参照すること。
- Ludii GDLの仕様に基づくとどうすればゲームのルールを実装できるかを熟考して検討してほしい。
- 机戦(cet2kai, cerke, cetkaik)に関する資料として、基本的なルール説明が cet2kaik-docs/ 配下のPDFファイルに入っている。曖昧性のない厳密な論理的ルールは、下記のオンライン統一規則にのみ記載されている。必要に応じてネット接続で情報を入手すること。
    - 統一規則（日本語版）:https://sites.google.com/view/cet2kaik/%E7%B5%B1%E4%B8%80%E8%A6%8F%E5%89%87?authuser=0
    - 統一規則（英語版）:https://sites.google.com/view/cet2kaik/%E4%BB%96%E8%A8%80%E8%AA%9E%E7%89%88-other-languages/the-standardized-rule-in-english?authuser=0
- Ludiiに関する資料は ludii-docs/ 配下のPDFファイルを読むこと。Ludii関連のPDFファイルはどれもページ数が膨大なので、コンテキストが溢れないよう、必要な部分に限定して参照するよう配慮すること。
- PDFファイルを読む時は、PDF skillを使用してファイルを読むこと。
- Ludii GDL（.lud）の妥当性確認には `./scripts/validate_ludii_gdl.sh <file.lud>` を使うこと。特定のルールセットやオプションを確認する場合は、`--ruleset "Ruleset Name"` または `--option "Category/Choice"` を追加する。初期化と合法手生成まで確認する場合は `--smoke --expect-initial-legal-min 1` を追加し、ランダム実行まで確認する場合は `--playouts N --max-actions M --seed S` を追加する。終局まで到達しないプレイアウトを失敗扱いにする場合は、さらに `--require-terminal` を指定する。
