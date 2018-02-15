// See LICENSE for license details.

package cdr

import chisel3._

class CDR(adc_width: Int = 5, fifo_width: Int = 8, space_counter_width: Int = 5, IF_value: Int = 15, shift_bits: Int = 40, CR_adjust_res: Int = 4) extends Module{
  val io = IO(new Bundle {
    val isig = Input(SInt(adc_width.W))
    val data_out = Output(UInt(fifo_width.W))
  })


  // Pseudo-interpolation and Zero Crossing Detection

  val prev_isig = RegInit(0.S(adc_width.W))
  val start_CR = RegInit(false.B)
  val zcross_detected = (prev_isig(adc_width-1) ^ isig(adc_width-1)).asBool
  val zcross_loc = Wire(Bool(1.W))								// Pseudo-interpolation. 0 for first half, 1 for second of one clock cycle

  prev := io.isig

  when (zcross_detected) {
  	start_CR := true.B											// This register gets set once at the very beginning
  	when (isig(adc_width-1) === 1.U) {							// Zero Crossing && new point is negative
  		zcross_loc := ((prev_isig + isig) >= 0.U)				// Sum of prev and curr is >= 0 means zero crossing happens in the second half
  	} .otherwise {												// Zero Crossing && new point is positive
  		zcross_loc := ((prev_isig + isig) < 0.U)				// Sum of prev and curr is < 0 means zero crossing happens in the second half
  	}
  }.otherwise {
  	zcross_loc := false.B										// This value is only used with zcross_detected, so nominally we set it to false with no side effects
  }

  // Space counter and Data demodulation
  val space_counter = RegInit(0.U(space_counter_width.W))
  val data_noclk = RegInit(0.U(1.W))
  val IF_compare = Wire(UInt((log2Up(IF_value)+1).W))

  IF_compare := IF_value.U

  when (!zcross_detected) {
  	space_counter := space_counter + 2.U						// No ZC, count up by two spaces
  	data_noclk := data_noclk									// Redundant. For illustrative purposes
  } .otherwise {
  	space_counter := 0.U(space_counter_width.W)					// ZC encountered, reset space_counter
  	data_noclk := Mux((space_counter + zcross_loc.asUInt) < IF_compare, 1.U, 0.U)
  }





  // Clock recovery

  val noclk_shiftreg = RegInit(Vec(shift_bits, UInt(1.W)).fromBits(0.U))

  for (i <- 0 until shift_bits-1) {
  	noclk_shiftreg((i+1).U) := noclk_shiftreg(i.U)
  }
  noclk_shiftreg(0.U) := data_noclk
  

  val data_sum = RegInit(0.S(log2Up(shift_bits*2).W))			// Could be +/- 40
  val mid_sum = RegInit(0.S(log2Up(shift_bits).W))				// Could be +/- 20
  val sym_period_counter = RegInit(0.U(log2Up(shift_bits).W))
  val shiftreg_ptr = RegInit((shift_bits/2).U(log2Up(shift_bits).W))
  val last_bit = RegInit(0.U(1.W))
  val recovered_bit = Wire(UInt(1.W))

  sym_period_counter := Mux(!start_CR || sym_period_counter === (shift_bits-1).U,
							0.U,
							sym_period_counter + 1.U)

  when (!start_CR) {
  	data_sum := noclk_shiftreg(shiftreg_ptr)
  } .elsewhen (sym_period_counter != 0.U) {
  	data_sum := data_sum + Mux(noclk_shiftreg(shiftreg_ptr) === 1.U, 1.S(2.W), -1.S(2.W))
  } .otherwise {
  	data_sum := Mux(noclk_shiftreg(shiftreg_ptr) === 1.U, 1.S(2.W), -1.S(2.W))
  }

  when(!start_CR) {
  	mid_sum := noclk_shiftreg(shiftreg_ptr)
  } .elsewhen (sym_period_counter === 0.U) {
    mid_sum := Mux(noclk_shiftreg(shiftreg_ptr) === 1.U, 1.S(2.W), -1.S(2.W))
  } .elsewhen (sym_period_counter < ((shift_bits)/2).U) {
    mid_sum := mid_sum + Mux(noclk_shiftreg(shiftreg_ptr) === 1.U, 1.S(2.W), -1.S(2.W))
  } .otherwise {
    mid_sum := mid_sum                      // Redundant. For illustrative purposes
  }

  last_bit := Mux(sym_period_counter === 0.U, (data_sum > 0.U).asUInt, last_bit)
  recovered_bit := Mux(sym_period_counter === 0.U, (data_sum > 0.U).asUInt, last_bit)

  val special_action = RegInit(0.U(1.W))
  val extra_bit = RegInit(0.U(1.W))
  val extra_pause = RegInit(0.U(1.W))

  when (start_CR && sym_period_counter === 0.U) {
    when ((data_sum > 0.U) && (mid_sum > data_sum - mid_sum) || (data_sum <= 0.U) && (mid_sum < data_sum - mid_sum)) {
      shiftreg_ptr := shiftreg_ptr + CR_adjust_res
      when (shiftreg_ptr + CR_adjust_res.U > (shift_bits-1).U) {
        special_action := 1.U
        extra_bit := ????
      }
    } .elsewhen ((data_sum > 0.U) && (mid_sum < data_sum - mid_sum) || (data_sum <= 0.U) && (mid_sum > data_sum - mid_sum)) {
      shiftreg_ptr := shiftreg_ptr - CR_adjust_res
      when (shiftreg_ptr < CR_adjust_res.U) {
        special_action := 1.U
        extra_pausea := 1.U
      }
    }
  }


}