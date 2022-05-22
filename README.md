# Graphic Attack Prototype

An Android App created as a real-time device-level prototype to show that we can protect internet users with Epilepsy or Chronic Migraine from graphic-based attacks.

The algorithms used were based on this paper: 
https://ieeexplore.ieee.org/document/7148104

This was tested on Google Pixel 4a.
Created with Android Studio Arctic Fox 2020 (Mac OS). Tested to also work on Windows. 

## Future Work:
- implement the special cases such as the flashing red and patterns that can also trigger Epilepsy. 

Gifs used for testing:
https://docs.google.com/presentation/d/17PBTRKwa89gZPMP3BoM1ahDHR9G-x_7Dh2bz13LJNp8/edit?usp=sharing

## User Interface: 

<img src="https://github.com/angellam278/graphicAttack/blob/master/ui.png" width="400">


## Filter Examples:

Will draw over dangerous flashing pixels with the selected filter.

Solid Grey

<img src="https://github.com/angellam278/graphicAttack/blob/master/grey_filter_example.png" width="400">

Linear Interpolation between current and next frames to ease the transition.

<img src="https://github.com/angellam278/graphicAttack/blob/master/lerp_filter_example.png" width="400">
