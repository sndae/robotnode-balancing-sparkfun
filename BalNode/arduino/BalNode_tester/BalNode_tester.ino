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

int ledPin = 13;

unsigned long startMs = 0;
long count;

void setup()
{
    // seed random number generator
    randomSeed(analogRead(0));
    
    pinMode(ledPin, OUTPUT);
    
    // open up serial port to phone (via BT adapter or USB)
    Serial.begin(38400);
    // 1ms should be more than enough for any data that is
    // being streamed to us
    //Serial.setTimeout(1);
    
    // open up software serial port to RoboClaw
    roboClaw.begin(38400);
    // turn off motors
    roboClaw.SpeedM1(rcAddress, 0);
    roboClaw.SpeedM2(rcAddress, 0);
    // set PID constants
    roboClaw.SetM1Constants(rcAddress,Kd,Kp,Ki,qpps);
    roboClaw.SetM2Constants(rcAddress,Kd,Kp,Ki,qpps); 
    
    // let user know we're ready
    Serial.println("Starting!");
    
    startMs = millis();
}



void loop()
{
    char cmd = '\0';
    
    // check for user commands
    if (Serial.available() > 1) { // we have at least two characters
        digitalWrite(ledPin, HIGH);
        if (Serial.read() == ':') {
            cmd = (char)Serial.read();
            if (cmd == 'R') {
                // user is requesting a read of motor speeds
                while (Serial.read() != '\n');
                
                GetClawSpeed();
            }
            else if (cmd == 'W') {
                // user is writing motor speeds
                ReadUserSpeed();
                // send command to RoboClaw
                roboClaw.SpeedM1M2(rcAddress, m1Speed, m2Speed);
            }
        }
        digitalWrite(ledPin, LOW);
    }
}


// command structure is as such:
// \n:W xxx yyy\n
// where : signals the start of a new command
// xxx is M1 speed, yyy is M2 speed
// \n is sent before command just in case we missed the last one
void ReadUserSpeed()
{
    Serial.read();
    m1Speed = Serial.parseInt();
    // read the separator
    Serial.read();
    m2Speed = Serial.parseInt();
    // read until terminating character
    while (Serial.read() != '\n');
    
   Serial.print("Got speeds ");
   Serial.print(m1Speed);
   Serial.print(" and ");
   Serial.println(m2Speed);
}

void GetClawSpeed()
{
    uint8_t status;
    bool valid;
    uint32_t speed;
    
    // zero motor speeds
    m1Speed = 0;
    m2Speed = 0;
      
    // get speeds from roboClaw 
    speed = roboClaw.ReadSpeedM1(rcAddress, &status, &valid);
    if (valid) {
        m1Speed = (int)speed;
    }
    speed = roboClaw.ReadSpeedM2(rcAddress, &status, &valid);
    if (valid) {
        m2Speed = (int)speed;
    }
  
    // Send results to user
    Serial.print(":R ");
    Serial.print(m1Speed);
    Serial.print(" ");
    Serial.println(m2Speed);
}
