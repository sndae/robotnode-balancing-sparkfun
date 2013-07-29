

unsigned long startMs = 0;

void setup()
{
    // seed random number generator
    randomSeed(analogRead(0));
    
    // open up serial port to phone
    Serial.begin(115200);
    // open up software serial port to RoboClaw
    
    // let user know we're ready
    Serial.println("Starting!");
    
    startMs = millis();
}

void loop()
{
    // broadcast timestamped message if 5 seconds have elapsed
    if ((millis() - startMs) > 5000) {
        startMs = millis();
        Serial.print("Hello World at t = ");
        Serial.print(((float)startMs)/1000.0);
        Serial.println(" s.");
    }
    
    // wait up to 1000ms before looping
    delay(random(0, 1000));
}
