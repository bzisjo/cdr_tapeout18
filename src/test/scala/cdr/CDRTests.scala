package cdr

import chisel3._
import chisel3.util._
import chisel3.iotesters._
import org.scalatest.{Matchers, FlatSpec}

class CDRTests(c: CDR) extends PeekPokeTester(c) {

}

class CDRTester extends ChiselFlatSpec {
	val backends = Array[String]("firrtl", "verilator")
	behavior of "CDR"
	backends foreach {backend =>
		it should s"idk, but something $backend" in {
			Driver(() => new CDR(5, 5, 15, 40, 1), backend) { c =>
				new CDRTester(c)
			} should be(true)
		}
	}
}