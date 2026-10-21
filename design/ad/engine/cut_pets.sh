#!/bin/bash
# Re-cuts the companion clips for the film: the same source videos and matting as the app
# (tools/cut_pet_video.py), but 512 px and 24 fps, every frame of the 10 s clip.
cd "$(dirname "$0")/../../.."
A=design/pets/anim; O=build/ad/pets
cut() { python3 tools/cut_pet_video.py "$A/$1.mp4" "$O/$2" --fps 24 --from 0 --to 239 --size 512 --quality 95 "${@:3}" > "$O/$2.log" 2>&1; echo "$2 $(ls $O/$2 | wc -l)"; }
cut nilufar_wave nilufar_idle & cut nilufar_think nilufar_think & cut nilufar_happy nilufar_happy & cut anorxon_happy anorxon_happy &
wait
cut anorxon_idle anorxon_idle & cut laylo_happy_green laylo_happy --key green --bg 4 & cut laylo_wave_green laylo_idle --key green --bg 4 & cut momiq_happy momiq_happy &
wait
cut momiq_idle momiq_idle & cut ohu_happy ohu_happy & cut ohu_idle ohu_idle &
wait
