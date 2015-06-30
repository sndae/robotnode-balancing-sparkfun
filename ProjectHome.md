The RobotNode Balancing is a balancing robot currently designed to use an arduino uno as a low level controller to talk to the motor controller and external sensors. An android phone (currently a Samsung Galaxy Nexus) is used as a high level controller and source of accelerometer, gyroscope, compass, gps, camera, and sound inputs.

The phone also provides bluetooth and wifi access though neither of these interfaces can be used for the Sparkfun competition.


This project contains code developed to test/run the physical robot used in the Sparkfun AVC as well as a Player/Stage simulation environment to test pretty much everything except the balancing code.

The current goal would be to take algorithms developed for the virtual robot and use them with little modification on the physical robot.

The stretch goal is to develop either a player (or ROS) driver for the physical robot. At that point, the exact same controller is used with the simulator and physical robot. One just passes the appropriate hostname: port for either the simulator or physical robot when starting the controller.