# AllBuf

### SynthDef factory producing buffer player synths for arbitrary amounts of input and output channels

AllBuf is a convenience class which produces all manners of SynthDefs for playing back buffers.

It makes sort of smart decisions about how to create patches based on your input parameters and it contains a method for factory producing all variations of SynthDefs for arbitrary input (number of channels in the buffer) and output channels.

It automatically creates variations of the SynthDefs containing pitch envelopes, low pass filter and filter envelopes which can all be easily used in patterns etc.

__For more information and examples, see the AllBuf help file. __

### Installation

Open up SuperCollider and evaluate the following line of code:
`Quarks.install("https://github.com/madskjeldgaard/allbuf")`
