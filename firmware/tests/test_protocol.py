import unittest
from pathlib import Path

def encode(buttons=0, lx=0, ly=0, rx=0, ry=0, lt=0, rt=0):
    def stick(v):
        return int(max(-127, min(127, round(v * 127)))) & 0xff
    def trigger(v):
        return int(max(0, min(255, round(v * 255))))
    return bytes([buttons & 255, (buttons >> 8) & 255,
                  stick(lx), stick(ly), stick(rx), stick(ry),
                  trigger(lt), trigger(rt), 0])

class ProtocolTest(unittest.TestCase):
    def test_neutral(self):
        self.assertEqual(encode(), bytes(9))
    def test_buttons(self):
        self.assertEqual(encode(buttons=0xA55A)[:2], bytes([0x5a,0xa5]))
    def test_axes_and_triggers(self):
        self.assertEqual(encode(lx=-1,ly=1,lt=1,rt=-1)[2:],bytes([129,127,0,0,255,0,0]))
    def test_packet_length(self):
        self.assertEqual(len(encode(buttons=65535,rx=0.5)),9)

if __name__ == "__main__":
    unittest.main()
