#!/bin/sh
# Generates the PNG and GIF test images in src/test/resources/me/hejl/image/{png,gif} with ImageMagick, from the
# picture of the JPEG tests, with ImageMagick's own decoding (over white) as the expected result. More variants
# are generated at test time by MagickTest where ImageMagick is installed. Run from this directory.
set -e
src=../resources/me/hejl/image/jpeg/source.ppm
png=../resources/me/hejl/image/png
gif=../resources/me/hejl/image/gif
mkdir -p "$png" "$gif"
# Colour, 8 bits per sample: decodes to the source exactly.
magick "$src" -strip -define png:color-type=2 -define png:bit-depth=8 "$png/rgb8.png"
# Palette with transparency, interlaced, on a smaller part of the picture.
magick "$src" -crop 37x29+10+10 +repage -strip -colors 60 -transparent "$(magick "$src" -crop 1x1+20+20 -format '%[pixel:p]' info:)" \
    -interlace PNG PNG8:"$png/palette-interlaced.png"
magick "$png/palette-interlaced.png" -background white -flatten -depth 8 "$png/palette-interlaced.ppm"
# Grey with alpha, 16 bits.
magick "$src" -crop 37x29+10+10 +repage -strip -colorspace Gray -alpha set -channel A -fx 'i/w' +channel \
    -define png:color-type=4 -define png:bit-depth=16 "$png/grey-alpha16.png"
magick "$png/grey-alpha16.png" -background white -flatten -depth 8 -type TrueColor "$png/grey-alpha16.ppm"
# GIF, interlaced, with a transparent colour.
magick "$src" -crop 37x29+10+10 +repage -strip -colors 100 -transparent "$(magick "$src" -crop 1x1+20+20 -format '%[pixel:p]' info:)" \
    -interlace GIF "$gif/interlaced.gif"
magick "$gif/interlaced.gif" -background white -flatten -depth 8 "$gif/interlaced.ppm"
