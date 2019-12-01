AllBufTest1 : UnitTest {
	test_check_classname {
		var result = AllBuf.new;
		this.assert(result.class == AllBuf);
	}
}


AllBufTester {
	*new {
		^super.new.init();
	}

	init {
		AllBufTest1.run;
	}
}
