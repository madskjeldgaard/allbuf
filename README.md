# AllBuf

### SynthDef factory producing buffer player synths for arbitrary amounts of input and output channels

AllBuf is a convenience class which produces all manners of SynthDefs for playing back buffers.

It makes sort of smart decisions about how to create patches based on your input parameters and it contains a method for factory producing all variations of SynthDefs for arbitrary input (number of channels in the buffer) and output channels.

It automatically creates variations of the SynthDefs containing pitch envelopes, low pass filter and filter envelopes which can all be easily used in patterns etc.

```
// Create SynthDefs for playing back buffers of 1-32 channels panned in stereo
// Watch the post window for the names of the variations
AllBuf.addSynths(outchans: 2, maxchansIn: 32, verbose: true);
```

The naming convention is \buf[inchans]i[outchans]

And then if it is a variation containg a pitch envelope add "pe" to the end of the name. 
Eg "buf2i4pe" for a buffer player with 2 buffer channels and a pitch envelope panned in 4 output channels.

And then if it is a variation containg a filter add "lpf" to the end of the name
Eg "buf8i4lpf" for a buffer player with 8 buffer channels and a low pass filter panned in 4 output channels.

And then if it is a variation containg a filter envelope add "e" to the end of the name
Eg "buf2i2lpfe" for a buffer player with 2 buffer channels and a low pass filter with added envelope panned in 2 output channels.

### Installation

Open up SuperCollider and evaluate the following line of code:
`Quarks.install("https://github.com/madskjeldgaard/allbuf")`
