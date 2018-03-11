// A fun CDR implementation
// Jonathan Wang

package cdr

import chisel3._
import chisel3.util._
import chisel3.experimental.FixedPoint
import dsptools.numbers._
import dsptools.numbers.implicits._
import dsptools.DspContext

// IO Bundle. Note that when you parameterize the bundle, you MUST override cloneType.
// This also creates x, y, z inputs/outputs (direction must be specified at some IO hierarchy level)
// of the type you specify via gen (must be Data:RealBits = UInt, SInt, FixedPoint, DspReal)
// class DspIo[T <: Data:Real](gen: => T) extends Bundle {
//   val isig = Input(gen.cloneType)
//   val data_out = Decoupled(UInt(1.W))
//   // override def cloneType: this.type = new DspIo(gen).asInstanceOf[this.type]
// }


class CDR[T <: Data:Real](gen:() => T, val adc_width: Int = 5, val space_counter_width: Int = 5, val IF_value: Int = 15, val shift_bits: Int = 40, val CR_adjust_res: Int = 4) extends Module{
  // val io = IO(new Bundle {
  //   val isig = Input(SInt(adc_width.W))
  //   val data_out = Decoupled(UInt(1.W))
  // })
  // val io = IO(new DspIo(gen))
  val io = IO(new Bundle {
    val isig: T = Input(gen().cloneType)
    val data_out = Decoupled(UInt(1.W))
  })


  // Pseudo-interpolation and Zero Crossing Detection

  // val prev_isig = RegInit(0.S(adc_width.W))    //DSP
  val prev_isig: T =  RegInit(gen().cloneType)
  val start_CR = RegInit(false.B)
  // val zcross_detected = (prev_isig(adc_width-1) ^ io.isig(adc_width-1))
  val testcomp = prev_isig
  val zcross_detected = ((prev_isig >= 0.S) && (io.isig < 0.S)) || ((prev_isig < 0.S) && (io.isig >= 0.S))
  val zcross_loc = Wire(Bool())								      // Pseudo-interpolation. 0 for first half, 1 for second of one clock cycle

  prev_isig := io.isig                                       // Store prev signal value

  when (zcross_detected) {
  	start_CR := true.B											            // This register gets set once at the very beginning
    when (io.isig < 0.S) {
  	// when (io.isig(adc_width-1) === 1.U) {							    // Zero Crossing && new point is negative
  		zcross_loc := ((prev_isig + io.isig) >= 0.S)				  // Sum of prev and curr is >= 0 means zero crossing happens in the second half
  	} .otherwise {												              // Zero Crossing && new point is positive
  		zcross_loc := ((prev_isig + io.isig) < 0.S)				  // Sum of prev and curr is < 0 means zero crossing happens in the second half
  	}
  }.otherwise {
  	zcross_loc := false.B										            // This value is only used with zcross_detected, so nominally we set it to false with no side effects
  }

  // Space counter and Data demodulation
  val space_counter = RegInit(0.U(space_counter_width.W))
  val data_noclk = RegInit(0.U(1.W))
  val IF_compare = Wire(UInt((log2Up(IF_value)+1).W))

  IF_compare := IF_value.U

  when (!zcross_detected) {
  	space_counter := space_counter + 2.U						    // No ZC, count up by two spaces
  	data_noclk := data_noclk									          // Redundant. For illustrative purposes
  } .otherwise {
  	space_counter := 0.U(space_counter_width.W)					// ZC encountered, reset space_counter
  	data_noclk := Mux((space_counter + zcross_loc.asUInt) < IF_compare, 1.U, 0.U)
  }
// TODO: VALIDATE TIMING




  // Clock recovery

  // val noclk_shiftreg = RegInit(Vec(shift_bits, UInt(1.W)).fromBits(0.U))
  val noclk_shiftreg = RegInit(Vec(Seq.fill(shift_bits)(0.U(1.W))))

  for (i <- 0 until shift_bits-1) {
  	noclk_shiftreg((i+1).U) := noclk_shiftreg(i.U)
  }
  noclk_shiftreg(0.U) := data_noclk
  

  val data_sum = RegInit(0.S(log2Up(shift_bits*2).W))			// Could be +/- 40
  val backup_sum = RegInit(0.S(log2Up(shift_bits*2).W))   // Could be +/- 40, used for extra_bit
  val mid_sum = RegInit(0.S(log2Up(shift_bits).W))				// Could be +/- 20
  val sym_period_counter = RegInit(0.U(log2Up(shift_bits).W))
  val shiftreg_ptr = RegInit(0.U(log2Up(shift_bits).W))   // Initialized to point to noclk_shiftreg(0.U)
  val last_bit = RegInit(0.U(1.W))
  val recovered_bit = Wire(UInt(1.W))
  val backup_sum_bit = RegInit(0.U(1.W))
  val first_cycle_done = RegInit(false.B)

  sym_period_counter := Mux(!start_CR || sym_period_counter === (shift_bits-1).U,
							0.U,
							sym_period_counter + 1.U)


  when (!start_CR) {
    data_sum := 0.S                                        // Don't care
    backup_sum := 0.S                                      // Don't care
  } .elsewhen (sym_period_counter != 0.U) {
    data_sum := data_sum + Mux(noclk_shiftreg(shiftreg_ptr) === 1.U, 1.S, -1.S)
    data_sum := data_sum + Mux(noclk_shiftreg(0.U) === 1.U, 1.S, -1.S)
  } .otherwise {
    data_sum := Mux(noclk_shiftreg(shiftreg_ptr) === 1.U, 1.S, -1.S)
    backup_sum := Mux(noclk_shiftreg(shiftreg_ptr) === 1.U, 1.S, -1.S)
  }

  when(!start_CR) {
    mid_sum := 0.S                                        // Don't care
  } .elsewhen (sym_period_counter === 0.U) {
    mid_sum := Mux(noclk_shiftreg(shiftreg_ptr) === 1.U, 1.S, -1.S)
  } .elsewhen (sym_period_counter < ((shift_bits)/2).U) {
    mid_sum := mid_sum + Mux(noclk_shiftreg(shiftreg_ptr) === 1.U, 1.S, -1.S)
  } .otherwise {
    mid_sum := mid_sum                                    // Redundant. For illustrative purposes
  }

  last_bit := Mux(sym_period_counter === 0.U, (data_sum > 0.S).asUInt, last_bit)          // This is a register to store the last bit
  recovered_bit := Mux(sym_period_counter === 0.U, (data_sum > 0.S).asUInt, last_bit)     // This is a wire that outputs the last recovered bit at the right clock period

  backup_sum_bit := Mux(sym_period_counter === 0.U, (backup_sum > 0.S).asUInt, backup_sum_bit)

  // These commented lines contain an incomplete clock deviation correction algorithm
  val extra_bit = RegInit(0.U(1.W))
  val extra_pause = RegInit(0.U(1.W))

  first_cycle_done := first_cycle_done || sym_period_counter === (shift_bits-1).U         // Don't want to trigger following logic before first cycle

  when (first_cycle_done && sym_period_counter === 0.U) { // start_CR is implied
    extra_bit := 0.U
    extra_pause := 0.U
    when ((data_sum > 0.S) && (mid_sum > data_sum - mid_sum) || (data_sum <= 0.S) && (mid_sum < data_sum - mid_sum)) {
      shiftreg_ptr := shiftreg_ptr + CR_adjust_res.U
      when (shiftreg_ptr + CR_adjust_res.U > (shift_bits-1).U) {
        extra_bit := 1.U                                  // Need to output what's already in noclk_shiftreg
      }
    } .elsewhen ((data_sum > 0.S) && (mid_sum < data_sum - mid_sum) || (data_sum <= 0.S) && (mid_sum > data_sum - mid_sum)) {
      shiftreg_ptr := shiftreg_ptr - CR_adjust_res.U
      when (shiftreg_ptr < CR_adjust_res.U) {
        extra_pause := 1.U                                // Need to not output anything for one symbol length, since data in noclk_shiftreg is already outputted
      }
    }
  }

  // Output
  val s_idle :: s_regular :: s_extra_bit :: s_extra_pause :: Nil = Enum(4)
  val state = Reg(init = s_idle)

  io.data_out.bits := 0.U
  io.data_out.valid := false.B

  when (state === s_idle) {
    // Next-state logic
    when (first_cycle_done && sym_period_counter === 0.U) {state := s_regular}

    // Current state output
    io.data_out.bits := 0.U
    io.data_out.valid := false.B
  }

  when (state === s_regular) {
    // Next-state logic
    when (io.data_out.ready) {
      when (extra_bit === 1.U) {state := s_extra_bit}
      .elsewhen (extra_pause === 1.U) {state := s_extra_pause}
      .otherwise {state := s_idle}
    }

    // Current state output
    io.data_out.bits := recovered_bit
    io.data_out.valid := true.B
  }

  when (state === s_extra_bit) {
    // Next-state logic
    when (io.data_out.ready) {state := s_idle}

    // Current state output
    io.data_out.bits := backup_sum_bit
    io.data_out.valid := true.B
  }
  when (state === s_extra_pause) {
    // Next-state logic
    when (sym_period_counter === 0.U) {state := s_idle}

    // Current state output
    io.data_out.bits := recovered_bit
    io.data_out.valid := false.B
  }
}