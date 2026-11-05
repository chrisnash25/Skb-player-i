#include <Arduino.h>
#include <BLEDevice.h>
#include <BLEServer.h>
#include <BLEUtils.h>
#include <BLEHIDDevice.h>
#include <BLE2902.h>
#include <BLESecurity.h>

// Experimental BLE HID gamepad + write-only command service.
// Pair and verify Android compatibility before relying on it.
static const char *CMD_SERVICE = "7a31f0e0-5bd1-4df5-b36e-6c8c1b319001";
static const char *CMD_CHAR = "7a31f0e0-5bd1-4df5-b36e-6c8c1b319002";
static BLEHIDDevice *hid = nullptr;
static BLECharacteristic *inputReport = nullptr;
static volatile bool connected = false;
static uint32_t lastCommand = 0;
static uint32_t lastReport = 0;
static uint8_t report[9] = {0};
// 16 buttons, four 8-bit axes, two 8-bit triggers, report ID 1.
static const uint8_t descriptor[] = {
  0x05,0x01,0x09,0x05,0xA1,0x01,0x85,0x01,
  0x05,0x09,0x19,0x01,0x29,0x10,0x15,0x00,0x25,0x01,
  0x75,0x01,0x95,0x10,0x81,0x02,
  0x05,0x01,0x09,0x30,0x09,0x31,0x09,0x33,0x09,0x34,
  0x15,0x81,0x25,0x7f,0x75,0x08,0x95,0x04,0x81,0x02,
  0x09,0x32,0x09,0x35,0x15,0x00,0x26,0xff,0x00,
  0x75,0x08,0x95,0x02,0x81,0x02,
  0xC0
};
static void neutral() { memset(report,0,sizeof(report)); }
class Connection : public BLEServerCallbacks {
  void onConnect(BLEServer*) override { connected = true; lastCommand = millis(); }
  void onDisconnect(BLEServer*) override { connected = false; neutral(); BLEDevice::startAdvertising(); }
};
class Commands : public BLECharacteristicCallbacks {
  void onWrite(BLECharacteristic *c) override {
    std::string data = c->getValue();
    // Exact 9-byte state packet; no unauthenticated text commands.
    if (!connected || data.size() != sizeof(report)) return;
    memcpy(report,data.data(),sizeof(report));
    // Byte 8 reserved for future versioning; only six axes/buttons are used.
    report[8] = 0;
    lastCommand = millis();
  }
};
void setup() {
  Serial.begin(115200);
  BLEDevice::init("SKB AI Controller");
  BLEServer *server = BLEDevice::createServer();
  server->setCallbacks(new Connection());
  hid = new BLEHIDDevice(server);
  inputReport = hid->inputReport(1);
  hid->manufacturer()->setValue("SKB Secure Hardware Solutions");
  hid->pnp(0x02,0x1234,0x0001,0x0100);
  hid->hidInfo(0x00,0x01);
  hid->reportMap((uint8_t*)descriptor,sizeof(descriptor));
  hid->startServices();
  BLEService *commandService = server->createService(CMD_SERVICE);
  BLECharacteristic *command = commandService->createCharacteristic(
      CMD_CHAR, BLECharacteristic::PROPERTY_WRITE);
  command->setCallbacks(new Commands());
  commandService->start();
  BLESecurity *security = new BLESecurity();
  security->setAuthenticationMode(ESP_LE_AUTH_REQ_SC_BOND);
  security->setCapability(ESP_IO_CAP_NONE);
  BLEAdvertising *advertising = BLEDevice::getAdvertising();
  advertising->setAppearance(0x03C4); // Generic gamepad
  advertising->addServiceUUID(hid->hidService()->getUUID());
  advertising->addServiceUUID(BLEUUID(CMD_SERVICE));
  advertising->start();
  Serial.println("SKB bridge advertising; BLE pairing and HID support unverified");
}
void loop() {
  uint32_t now = millis();
  if ((uint32_t)(now-lastCommand) > 500) neutral();
  if (connected && (uint32_t)(now-lastReport)>=16) {
    lastReport=now;
    inputReport->setValue(report,8);
    inputReport->notify();
  }
  delay(2);
}
