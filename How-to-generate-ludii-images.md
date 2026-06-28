# Ludii用画像の生成手順

この手順は、`cerke/pieces2`にある机戦の駒画像から、Ludiiの`graphics` metadataで使えるSVGを生成する方法をまとめたものです。元画像はPNGですが、Ludiiの駒前景画像はSVG名で参照するのが安定します。ここではPNGを二値化し、`potrace`でSVG化した後、Ludiiの簡易SVG描画器が読める形へ整形します。

## 元画像

駒画像の元データは、リポジトリ内の`cerke/pieces2`にあります。今回使った画像は黒駒側のPNGで、`bnuak.png`、`bkauk.png`、`bgua.png`、`bkaun.png`、`bdau.png`、`bmaun.png`、`bkua.png`、`btuk.png`、`buai.png`、`bio.png`、`btam.png`です。Ludii側では同じSVGを色指定で塗り分けるため、赤駒用のPNGを別に変換する必要はありません。

## 変換の前提

変換にはImageMagickと`potrace`を使います。ImageMagickでPNGから余白を切り、グレースケール二値画像に変換します。次に`potrace`で輪郭をSVG化し、最後に`potrace`が出力する座標変換をパス座標へ焼き込みます。

Ludiiの`SVGtoImage`は、通常のブラウザほどSVG仕様を広く解釈しません。特に`<g transform="...">`、`style`、`fill-rule`、親要素の`fill`や`stroke`に依存すると表示が崩れます。そのため、最終SVGはトップレベルの`<path d="..."/>`だけを持つ単純な形式にします。

## 生成手順

作業ディレクトリはリポジトリルート、つまり`/home/stepney141/board-games/ludii-cet2kaik`です。まずPNGをPBMへ変換し、`potrace`で一時SVGを作ります。

```sh
mkdir -p tmp

for src in nuak kauk gua kaun dau maun kua tuk uai io tam; do
  magick "cerke/pieces2/b${src}.png" \
    -alpha remove \
    -background white \
    -crop 210x210+23+23 \
    -resize 256x256 \
    -colorspace Gray \
    -threshold 50% \
    "tmp/${src}.pbm"

  potrace -s --flat "tmp/${src}.pbm" -o "tmp/${src}_raw.svg"
done
```

重要なのは、ここで`-flip`を入れないことです。`potrace`のSVG出力はY軸反転を`<g transform>`で表現しますが、後段でその変換を座標へ焼き込みます。ImageMagick側でも反転すると、最終画像が上下左右に反転します。

次に、一時SVGから`path`を取り出し、`<g transform="translate(0,256) scale(0.1,-0.1)">`相当の変換をパス座標に焼き込みます。

```sh
python - <<'PY'
from pathlib import Path
import re

CMD_RE = re.compile(r'[AaCcHhLlMmQqSsTtVvZz]|[-+]?(?:\d*\.\d+|\d+\.?)(?:[eE][-+]?\d+)?')
PATH_RE = re.compile(r'<path\b[^>]*\bd="([^"]*)"', re.S)
COUNTS = {'M': 2, 'L': 2, 'T': 2, 'H': 1, 'V': 1, 'C': 6, 'S': 4, 'Q': 4, 'A': 7, 'Z': 0}

def fmt(v):
    if abs(v) < 1e-9:
        v = 0.0
    return (f'{v:.3f}'.rstrip('0').rstrip('.')) or '0'

def transform_number(cmd, index, value):
    upper = cmd.upper()
    absolute = cmd.isupper()
    if upper == 'H':
        return value * 0.1
    if upper == 'V':
        return 256.0 - value * 0.1 if absolute else -value * 0.1
    if upper == 'A':
        if index in (0, 1):
            return value * 0.1
        if index in (2, 3, 4):
            return value
        if index == 5:
            return value * 0.1 if absolute else value * 0.1
        return 256.0 - value * 0.1 if absolute else -value * 0.1
    if index % 2 == 0:
        return value * 0.1
    return 256.0 - value * 0.1 if absolute else -value * 0.1

def transform_path(d):
    toks = CMD_RE.findall(d)
    out = []
    i = 0
    cmd = None
    while i < len(toks):
        tok = toks[i]
        if re.fullmatch(r'[AaCcHhLlMmQqSsTtVvZz]', tok):
            cmd = tok
            out.append(cmd)
            i += 1
            if cmd.upper() == 'Z':
                continue
        if cmd is None:
            raise ValueError('Path data starts without a command')
        count = COUNTS[cmd.upper()]
        if count == 0:
            continue
        first = True
        while i < len(toks) and not re.fullmatch(r'[AaCcHhLlMmQqSsTtVvZz]', toks[i]):
            group = toks[i:i + count]
            if len(group) < count or any(re.fullmatch(r'[AaCcHhLlMmQqSsTtVvZz]', x) for x in group):
                break
            if not first:
                out.append(cmd)
            first = False
            out.extend(fmt(transform_number(cmd, j, float(v))) for j, v in enumerate(group))
            i += count
    return ' '.join(out)

for raw in sorted(Path('tmp').glob('*_raw.svg')):
    name = raw.name[:-len('_raw.svg')]
    match = PATH_RE.search(raw.read_text())
    if not match:
        raise SystemExit(f'no path in {raw}')
    d = transform_path(match.group(1))
    text = f'<svg xmlns="http://www.w3.org/2000/svg" width="256" height="256" viewBox="0 0 256 256"><path d="{d}"/></svg>\n'
    Path('assets/cetkaik').mkdir(parents=True, exist_ok=True)
    Path(f'assets/cetkaik/cerke_glyph_{name}.svg').write_text(text)
    print(name)
PY
```

この処理により、`Cetkaik.lud`から参照するSVGが`assets/cetkaik`に作られます。`Cetkaik.lud`ではこの相対パスを明示して参照するため、画像ファイルをリポジトリ直下へ置く必要はありません。

## 盤の斜線

盤上の斜線は、駒画像から生成したものではありません。`show Symbol "line"`を回転して使うと、セルごとの丸め誤差とアンチエイリアスで線がぶれます。そのため、回転不要の対角線SVGを手で用意し、`assets/cetkaik/cerke_diag_backslash.svg`と`assets/cetkaik/cerke_diag_slash.svg`として置きます。

```xml
<svg xmlns="http://www.w3.org/2000/svg" width="256" height="256" viewBox="0 0 256 256"><path d="M 0 9 L 9 0 L 256 247 L 247 256 Z"/></svg>
```

```xml
<svg xmlns="http://www.w3.org/2000/svg" width="256" height="256" viewBox="0 0 256 256"><path d="M 247 0 L 256 9 L 9 256 L 0 247 Z"/></svg>
```

## 検証

SVGの構文がLudii向けの単純形式から外れていないか確認します。次のコマンドで何も出なければ、`<g transform>`などの未対応要素に依存していません。

```sh
rg "<g|transform|DOCTYPE|metadata|fill-rule|style" \
  assets/cetkaik/*.svg
```

元PNGと生成SVGの向きは、ImageMagickで並べて確認できます。

```sh
magick montage \
  cerke/pieces2/bnuak.png assets/cetkaik/cerke_glyph_nuak.svg \
  cerke/pieces2/bio.png assets/cetkaik/cerke_glyph_io.svg \
  cerke/pieces2/btam.png assets/cetkaik/cerke_glyph_tam.svg \
  -background white -tile 2x -geometry 160x160+12+12 \
  tmp/fixed_orientation_compare.png
```

`.lud`の妥当性は、プロジェクト付属の検査スクリプトで確認します。

```sh
./scripts/validate_ludii_gdl.sh Cetkaik.lud
```

## Ludii側での参照

`Cetkaik.lud`では、生成したSVGを`assets/cetkaik`からの相対パスで参照します。たとえば、`cerke_glyph_nuak.svg`は`image:"assets/cetkaik/cerke_glyph_nuak.svg"`として指定します。P1とP2で駒の向きを変える場合は、`piece Foreground P1 ...`と`piece Foreground P2 ... rotation:180`のように、プレイヤー別にmetadataを分けます。

文字SVGの`piece Foreground`には、`fillColour:(colour White)`と文字色の`edgeColour`を両方指定します。Ludiiの`SVGtoImage`では、`fillColour`が`z`で閉じたサブパスを個別に塗るため、文字色を指定すると閉路を含む文字の白抜き部分まで塗り潰されます。一方、`fillColour`を省略するとLudiiが駒所有者のプレイヤー色を補うため、P2の白抜き内部に薄い灰色が残ります。白を明示してから`edgeColour`でパス全体を塗ると、駒の白地と文字の白抜きが一致します。
