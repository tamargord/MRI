"""End-to-end CLI/video checks using Python's standard library, FFmpeg and FFprobe."""
import json
import math
import os
from pathlib import Path
import subprocess
import sys
import tempfile

ROOT = Path(__file__).resolve().parents[1]
CP = str(ROOT / 'out')
checks = 0


def check(ok, label):
    global checks
    checks += 1
    if not ok:
        raise AssertionError(label)


def run(args, *, cwd, env=None):
    return subprocess.run(args, cwd=cwd, env=env, capture_output=True, check=False)


def project_reference(points):
    # Independent world-space interpolation; fixture inverse is analytic.
    a1, a2, b, c = points if len(points) == 4 else [points[0], points[0], *points[1:]]
    distance = lambda a, b: math.sqrt(sum((u-v)**2 for u, v in zip(a, b)))
    l1, l2, base = distance(a1, b), distance(a2, c), distance(b, c)
    width, height = math.ceil(base), math.ceil(l1)
    image = [[0]*width for _ in range(height)]
    for y in range(height):
        left = [a+(v-a)*min(y+1, l1)/l1 for a, v in zip(a1, b)]
        right = [a+(v-a)*min(y+1, l2)/l2 for a, v in zip(a2, c)]
        span = distance(left, right)
        for k in range(math.floor(span+1e-9)+1):
            world = [a+(v-a)*k/span for a, v in zip(left, right)]
            vx, vy, vz = (world[1]+20)/2, 10-world[0], (world[2]-30)/1.5
            x, yy, z = [math.floor(v+0.5) for v in (vx, vy, vz)]
            col = math.floor((base-span)/2+k+0.5)
            if 0 <= col < width and 0 <= x < 8 and 0 <= yy < 7 and 0 <= z < 6:
                image[y][col] = x+10*yy+100*z
    maximum = max(map(max, image))
    return [[math.floor(v*255/maximum+0.5) for v in row] for row in image]


def decode(path, cwd):
    probe = run(['ffprobe', '-v', 'error', '-count_frames', '-show_entries',
                 'stream=width,height,nb_read_frames,r_frame_rate', '-of', 'json', str(path)], cwd=cwd)
    check(probe.returncode == 0, 'ffprobe success')
    stream = json.loads(probe.stdout)['streams'][0]
    decoded = run(['ffmpeg', '-v', 'error', '-i', str(path), '-f', 'rawvideo',
                   '-pix_fmt', 'gray', 'pipe:1'], cwd=cwd)
    check(decoded.returncode == 0, 'video decodes')
    return stream, decoded.stdout


def main():
    with tempfile.TemporaryDirectory(prefix='mri-integration-') as temp:
        work = Path(temp)
        volume = ROOT/'datasets/coded-u16-rotated.nrrd'
        csv = ROOT/'datasets/mixed-planes.csv'
        output = work/'result with spaces.mp4'
        stale = work/'frame_000.png'
        stale.write_bytes(b'This stale file is deliberately not a valid PNG')
        command = ['java', '-cp', CP, 'Projection', str(volume), str(csv), str(output)]
        completed = run(command, cwd=work)
        check(completed.returncode == 0, completed.stderr.decode(errors='replace'))
        stream, decoded = decode(output, work)
        check(stream['nb_read_frames'] == '2', 'exact mixed frame count')
        check(stream['r_frame_rate'] == '30/1', '30 playback FPS')
        width, height = stream['width'], stream['height']
        check((width, height) == (12, 8), 'common canvas with varying plane sizes')
        check(len(decoded) == width*height*2, 'decoded frame byte count')
        rows = [list(map(float, line.split(','))) for line in csv.read_text().splitlines() if line.strip()]
        for index, row in enumerate(rows):
            points = [row[i:i+3] for i in range(0, len(row), 3)]
            image = project_reference(points)
            expected = [0]*(width*height)
            dx, dy = (width-len(image[0]))//2, (height-len(image))//2
            for y, line in enumerate(image):
                expected[(y+dy)*width+dx:(y+dy)*width+dx+len(line)] = line
            actual = decoded[index*width*height:(index+1)*width*height]
            differences = [abs(a-b) for a, b in zip(actual, expected)]
            mae = sum(differences)/len(differences)
            check(mae <= 8 and max(differences) <= 30, f'frame {index} content/order against reference: MAE={mae:.3f}, max={max(differences)}')
            print(f'Frame {index}: independent grayscale reference, decoded MAE={mae:.3f}, max error={max(differences)}')
        check(stale.read_bytes().startswith(b'This stale'), 'stale PNG untouched and excluded')
        short = work/'one.csv'
        short.write_text(csv.read_text().splitlines()[0]+'\n')
        rerun = run(['java', '-cp', CP, 'Projection', str(volume), str(short), str(output)], cwd=work)
        check(rerun.returncode == 0, 'overwrite previous video with shorter run')
        shorter, _ = decode(output, work)
        check(shorter['nb_read_frames'] == '1', 'no leftover frames on rerun')
        original = output.read_bytes()
        for executable in (str(work/'missing-ffmpeg'), sys.executable):
            env = dict(os.environ, FFMPEG=executable)
            failure = run(command, cwd=work, env=env)
            check(failure.returncode != 0 and b'Error:' in failure.stderr, f'missing/failed encoder is a nonzero CLI error: executable={executable}, status={failure.returncode}, stderr={failure.stderr!r}, stdout={failure.stdout!r}')
            check(output.read_bytes() == original, 'failed encoder preserves previous output')
        for arguments in ([], [str(volume), str(csv)], [str(volume), str(csv), str(output), 'extra']):
            failure = run(['java', '-cp', CP, 'Projection', *arguments], cwd=work)
            check(failure.returncode != 0 and b'Usage:' in failure.stderr, 'argument validation')
        bad_csv = work/'bad.csv'
        bad_csv.write_text('NaN,0,0,1,0,0,0,1,0\n')
        failure = run(['java', '-cp', CP, 'Projection', str(volume), str(bad_csv), str(output)], cwd=work)
        check(failure.returncode != 0 and b'CSV line 1' in failure.stderr, 'CSV row error identifies location')
        check(output.read_bytes() == original, 'invalid CSV preserves output')
        check(not list(work.glob('mri-video-*')), 'temporary video files removed')
    print(f'PASS: {checks} end-to-end checks')


if __name__ == '__main__':
    main()
