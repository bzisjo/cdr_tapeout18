// See LICENSE for license details.

package cdr

import chisel3._

class CDR(adc_width: Int = 5, fifo_width: Int = 8) extends Module{
  val io = IO(new Bundle {
    val istream = Input(SInt(adc_width.W))
    val data_out = Output(UInt(fifo_width.W))
  })

/*  def abs_sig(a: SInt): UInt = {
  	when 
  }
*/
  val prev_istream = RegInit(0.U(adc_width.W))
  prev := io.istream

  val zcross_detected = prev_istream(adc_width-1) ^ istream(adc_width-1)
  val zcross_loc = Wire(UInt(1.W))								// Pseudo-interpolation. 0 for first half, 1 for second of one clock cycle

  when (zcross_detected === 1.U) {
  	when (istream(adc_width-1) === 1.U) {						// Zero Crossing && new point is negative
  		zcross_loc := ((prev_istream + istream) >= 0.U).asUInt	// Sum of prev and curr is >= 0 means zero crossing happens in the second half
  	}.otherwise {												// Zero Crossing && new point is positive
  		zcross_loc := ((prev_istream + istream) < 0.U).asUInt	// Sum of prev and curr is < 0 means zero crossing happens in the second half
  	}
  }.otherwise {
  	zcross_loc := 0.U
  }


}