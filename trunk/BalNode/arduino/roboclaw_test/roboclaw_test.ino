#include <BMSerial.h>
#include <RoboClaw.h>

#define rcAddress 0x80 // RoboClaw address
#define Kp 0x00010000
#define Ki 0x00008000
#define Kd 0x00004000
#define qpps 12000 // nominal motor speed is 10667 counts per second

// Set up RoboClaw on pins 12,13 (not used for pwm by the uno)
// rx on 10, tx on 11
RoboClaw roboClaw(10,11);
int m1Speed = 0;
int m2Speed = 0;

unsigned long startMs = 0;


void setup()
{
    // seed random number generator
    randomSeed(analogRead(0));
    
    // open up serial port to phone
    Serial.begin(38400);
    // open up software serial port to RoboClaw
    roboClaw.begin(38400);
    // turn off motors
    roboClaw.SpeedM1(rcAddress, 0);
    roboClaw.SpeedM2(rcAddress, 0);
    // set PID constants
    roboClaw.SetM1Constants(rcAddress,Kd,Kp,Ki,qpps);
    roboClaw.SetM2Constants(rcAddress,Kd,Kp,Ki,qpps);
    // set battery min/max voltage
    roboClaw.SetMinVoltageMainBattery(rcAddress, 0);
    roboClaw.SetMaxVoltageMainBattery(rcAddress, 154);
    
    // let user know we're ready
    Serial.println("Starting!");
    
    startMs = millis();
}


void loop()
{
    // check for user command
    if (Serial.available() > 0) {
        if (Serial.read() == ':') {
            ReadSerialCommand();
            // send command to RoboClaw
            roboClaw.SpeedM1M2(rcAddress, m1Speed, m2Speed);
        }
    }
    
    // update display every 500ms
    if ((millis() - startMs) > 500) {
        UpdateDisplay();
        startMs = millis();
    }
}


// command structure is as such:
// \n:xxx yyy\n
// where : signals the start of a new command
// xxx is M1 speed, yyy is M2 speed
// \n is sent before command just in case we missed the last one
void ReadSerialCommand()
{
    m1Speed = Serial.parseInt();
    // read the separator
    Serial.read();
    m2Speed = Serial.parseInt();
    // read until terminating character
    while (Serial.read() != '\n');
    Serial.println(m1Speed);
}

void UpdateDisplay()
{
  uint8_t status;
  bool valid;
  
  uint32_t enc1= roboClaw.ReadEncM1(rcAddress, &status, &valid);
  if(valid){
    Serial.print("Encoder1:");
    Serial.print(enc1,HEX);
    Serial.print(" ");
    Serial.print(status,HEX);
    Serial.print(" ");
  }
  uint32_t enc2 = roboClaw.ReadEncM2(rcAddress, &status, &valid);
  if(valid){
    Serial.print("Encoder2:");
    Serial.print(enc2,HEX);
    Serial.print(" ");
    Serial.print(status,HEX);
    Serial.print(" ");
  }
  uint32_t speed1 = roboClaw.ReadSpeedM1(rcAddress, &status, &valid);
  if(valid){
    Serial.print("Speed1:");
    Serial.print(speed1);
    Serial.print(" ");
  }
  uint32_t speed2 = roboClaw.ReadSpeedM2(rcAddress, &status, &valid);
  if(valid){
    Serial.print("Speed2:");
    Serial.print(speed2);
    Serial.print(" ");
  }
  Serial.println();
}
