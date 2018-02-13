// See LICENSE for license details.

package cdr

import chisel3._

class CDR(adc_width: Int = 5, fifo_width: Int = 8, space_counter_width: Int = 5, IF_value: Int = 15, shift_bits: Int = 40) extends Module{
  val io = IO(new Bundle {
    val isig = Input(SInt(adc_width.W))
    val data_out = Output(UInt(fifo_width.W))
  })

/*  def abs_sig(a: SInt): UInt = {
  	when 
  }
*/

  // Pseudo-interpolation and Zero Crossing Detection

  val prev_isig = RegInit(0.S(adc_width.W))
  prev := io.isig

  val zcross_detected = (prev_isig(adc_width-1) ^ isig(adc_width-1)).asBool
  val zcross_loc = Wire(Bool(1.W))								// Pseudo-interpolation. 0 for first half, 1 for second of one clock cycle

  when (zcross_detected) {
  	when (isig(adc_width-1) === 1.U) {							// Zero Crossing && new point is negative
  		zcross_loc := ((prev_isig + isig) >= 0.U)				// Sum of prev and curr is >= 0 means zero crossing happens in the second half
  	} .otherwise {												// Zero Crossing && new point is positive
  		zcross_loc := ((prev_isig + isig) < 0.U)				// Sum of prev and curr is < 0 means zero crossing happens in the second half
  	}
  }.otherwise {
  	zcross_loc := false.B
  }

  // Space counter and Data demodulation
  val space_counter = RegInit(0.U(space_counter_width.W))
  val data_noclk = RegInit(0.S(2.W))
  val IF_compare = Wire(IF_value.U((log2Up(IF_value)+1).W))

  when (!zcross_detected) {
  	space_counter := space_counter + 2.U						// No ZC, count up by two spaces
  } .otherwise {
  	space_counter := 0.U(space_counter_width.W)					// ZC encountered, reset space_counter
  	data_noclk := Mux((space_counter + zcross_loc.asUInt) < IF_compare, 1.S(2.W), -1.S(2.W))
  }


  // Clock recovery
  val noclk_shiftreg = RegInit(Vec(shift_bits, SInt(2.W)).fromBits(0.S))		// TODO: Parameterize this?

  for (i <- 0 until shift_bits-1) {
  	noclk_shiftreg((i+1).U) := noclk_shiftreg(i.U)
  }
  noclk_shiftreg(0.U) := data_noclk

  val data_sum = RegInit(0.S(log2Up(shift_bits*2).W))			// Could be +/- 40
  val mid_sum = RegInit(0.S(log2Up(shift_bits).W))				// Could be +/- 20
  val sym_period_counter = RegInit(0.U(log2Up(shift_bits).W))
  val shiftreg_ptr = RegInit((shift_bits/2).U(log2Up(shift_bits).W))
  val recovered_bit = RegInit(0.U(1.W))

  sym_period_counter := Mux((sym_period_counter === shift_bits-1) || noclk_shiftreg(shiftreg_ptr) === 0.S, 
  							0.U, 
  							sym_period_counter + 1.U)
  						// If the pointer points to a 0, then no data as been shifted in yet. Wait until we see data before incrementing it.

  data_sum := Mux((noclk_shiftreg(shiftreg_ptr) === 0.S) || sym_period_counter === 0.U,
				  noclk_shiftreg(shiftreg_ptr),
				  data_sum + noclk_shiftreg(shiftreg_ptr))

  when ((noclk_shiftreg(shiftreg_ptr) === 0.S) || sym_period_counter === 0.U) {
  	mid_sum := noclk_shiftreg(shiftreg_ptr)
  } .elsewhen(sym_period_counter < ((shift_bits-1)/2).U) {
  	mid_sum := mid_sum + noclk_shiftreg(shiftreg_ptr)
  } .otherwise {
  	mid_sum := mid_sum											// Redundant. For illustrative purposes
  }



  recovered_bit := Mux(sym_period_counter === 0.U, 
  					   (data_sum > 0.U).asUInt, 
  					   recovered_bit)



}