#!/bin/sh
# Generates the JPEG test images in src/test/resources/me/hejl/image/jpeg with libjpeg-turbo (cjpeg), ImageMagick
# and exiftool. The source is a synthetic picture with smooth areas, edges and fine lines, at a size that is not a
# multiple of 16 so that partial blocks are covered. Run from this directory; the results are committed.
set -e
out=../resources/me/hejl/image/jpeg
mkdir -p "$out"
magick -size 67x51 -seed 7 plasma:orange-navy \
    -stroke white -draw 'line 3,4 60,45' -stroke black -draw 'line 0,50 66,0' \
    -fill none -stroke yellow -draw 'circle 33,25 33,12' -depth 8 -strip "$out/source.ppm"
src="$out/source.ppm"
cjpeg -quality 95 "$src" > "$out/baseline-420.jpg"
cjpeg -quality 95 -sample 1x1 "$src" > "$out/baseline-444.jpg"
cjpeg -quality 95 -sample 2x1 "$src" > "$out/baseline-422.jpg"
cjpeg -quality 95 -sample 1x2 "$src" > "$out/baseline-440.jpg"
cjpeg -quality 95 -sample 4x1 "$src" > "$out/baseline-411.jpg"
cjpeg -quality 95 -sample 2x2,2x1,1x2 "$src" > "$out/baseline-mixed.jpg"
cjpeg -quality 95 -optimize "$src" > "$out/optimized.jpg"
cjpeg -quality 95 -restart 1 "$src" > "$out/restart-rows.jpg"
cjpeg -quality 95 -restart 3B "$src" > "$out/restart-blocks.jpg"
cjpeg -quality 95 -rgb "$src" > "$out/rgb.jpg"
cjpeg -quality 95 -grayscale "$src" > "$out/grey.jpg"
cjpeg -quality 95 -grayscale -progressive "$src" > "$out/grey-progressive.jpg"
cjpeg -quality 95 -progressive "$src" > "$out/progressive-420.jpg"
cjpeg -quality 95 -progressive -sample 1x1 "$src" > "$out/progressive-444.jpg"
cjpeg -quality 95 -progressive -restart 2B "$src" > "$out/progressive-restart.jpg"
# One scan per component, without progression.
scans=$(mktemp)
printf '0;\n1;\n2;\n' > "$scans"
cjpeg -quality 95 -scans "$scans" "$src" > "$out/sequential-scans.jpg"
rm "$scans"
cjpeg -quality 95 -arithmetic "$src" > "$out/arithmetic.jpg"
cjpeg -quality 95 -precision 12 "$src" > "$out/precision-12.jpg"
magick "$src" -colorspace CMYK -quality 95 "$out/cmyk.jpg"
cp "$out/baseline-420.jpg" "$out/orientation-6.jpg"
exiftool -q -overwrite_original -n -Orientation=6 "$out/orientation-6.jpg"
