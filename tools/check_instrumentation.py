#!/usr/bin/env python3
import pathlib, re, sys
result = pathlib.Path(sys.argv[1]).read_text()
if not re.search(r'OK \(\d+ tests?\)', result) or any(s in result for s in ['FAILURES!!!', 'INSTRUMENTATION_FAILED', 'Process crashed']):
    raise SystemExit('Android instrumentation checks failed; inspect the test report.')
print('Android render and remote-control checks passed.')
