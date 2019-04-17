// A Shift-register based Clock Recovery Module
// Jonathan Wang
package cdr

import chisel3._
import chisel3.util._

class CR(shift_bits: Int = 40, CR_adjust_res: Int = 4) extends Module{
    val io = IO(new Bundle{
        val data_noclk = Input(UInt(1.W))
        val data_out = Decoupled(UInt(1.W))
    })
// Clock recovery
  val noclk_shiftreg = RegInit(Vec(Seq.fill(shift_bits)(0.U(1.W))))

  for (i <- 0 until shift_bits-1) {
    noclk_shiftreg((i+1).U) := noclk_shiftreg(i.U)
  }
  noclk_shiftreg(0.U) := io.data_noclk
  

  val data_sum = RegInit(0.S(log2Ceil(shift_bits*2).W))           // Could be +/- 40
  val backup_sum = RegInit(0.S(log2Ceil(shift_bits*2).W))         // Could be +/- 40, used for extra_bit
  val mid_sum = RegInit(0.S(log2Ceil(shift_bits).W))              // Could be +/- 20
  val sym_period_counter = RegInit(0.U(log2Ceil(shift_bits).W))   // Counter for 40 symbol period
  val shiftreg_ptr = RegInit(0.U(log2Ceil(shift_bits).W))         // Initialized to point to noclk_shiftreg(0.U)
  val last_bit = RegInit(0.U(1.W))
  val recovered_bit = Wire(UInt(1.W))
  val backup_sum_bit = RegInit(0.U(1.W))
  val first_cycle_done = RegInit(false.B)

  sym_period_counter := Mux(sym_period_counter === (shift_bits-1).U,
                            0.U,
                            sym_period_counter + 1.U)

  //data_sum accumulation
  when (sym_period_counter =/= 0.U) {
    data_sum := data_sum + Mux(noclk_shiftreg(shiftreg_ptr) === 1.U, 1.S, -1.S)
    backup_sum := backup_sum + Mux(noclk_shiftreg(0.U) === 1.U, 1.S, -1.S)
  } .otherwise {
    data_sum := Mux(noclk_shiftreg(shiftreg_ptr) === 1.U, 1.S, -1.S)
    backup_sum := Mux(noclk_shiftreg(0.U) === 1.U, 1.S, -1.S)
  }

  //mid_sum accumulation
  when (sym_period_counter === 0.U) {
    mid_sum := Mux(noclk_shiftreg(shiftreg_ptr) === 1.U, 1.S, -1.S)
  } .elsewhen (sym_period_counter < ((shift_bits)/2).U) {
    mid_sum := mid_sum + Mux(noclk_shiftreg(shiftreg_ptr) === 1.U, 1.S, -1.S)
  } .otherwise {
    mid_sum := mid_sum
  }


  // the register to store the last bit
  last_bit := Mux(sym_period_counter === 0.U, (data_sum > 0.S).asUInt, last_bit)
  // the  wire that outputs the last recovered bit at the right clock period
  recovered_bit := Mux(sym_period_counter === 0.U, (data_sum > 0.S).asUInt, last_bit)

  backup_sum_bit := Mux(sym_period_counter === 0.U, (backup_sum > 0.S).asUInt, backup_sum_bit)


  // Clock deviation correction algorithm (how to move the shiftreg_ptr)
  val extra_bit = RegInit(0.U(1.W))
  val extra_pause = RegInit(0.U(1.W))

  // Don't want to trigger following logic before first cycle done
  first_cycle_done := first_cycle_done || sym_period_counter === (shift_bits-1).U


  when (first_cycle_done && sym_period_counter === 0.U) {
    extra_bit := 0.U
    extra_pause := 0.U
    when (((data_sum > 0.S) && (mid_sum > data_sum - mid_sum)) || ((data_sum <= 0.S) && (mid_sum < data_sum - mid_sum))) {
      when ((shiftreg_ptr + CR_adjust_res.U > (shift_bits-1).U)) {
        when (extra_pause === 0.U) {
          shiftreg_ptr := shiftreg_ptr + CR_adjust_res.U - shift_bits.U
          // Need to output what's already in noclk_shiftreg immediately
          extra_bit := 1.U
          // printf(p"shifted+ with extra_bit\n")
        }
      } .otherwise {
        shiftreg_ptr := shiftreg_ptr + CR_adjust_res.U
        // printf(p"shifted+\n")
      }
    } .elsewhen (((data_sum > 0.S) && (mid_sum < data_sum - mid_sum)) || ((data_sum <= 0.S) && (mid_sum > data_sum - mid_sum))) {
      when ((shiftreg_ptr < CR_adjust_res.U)) {
        when (extra_bit === 0.U){
          shiftreg_ptr := shiftreg_ptr + shift_bits.U - CR_adjust_res.U
          // Need to not output anything for one symbol length, since data in noclk_shiftreg is already outputted
          extra_pause := 1.U
          // printf(p"shifted- with extra_pause\n")
        }
      } .otherwise {
        shiftreg_ptr := shiftreg_ptr - CR_adjust_res.U
        // printf(p"shifted-\n")
      }
    }
  }

  // Output
  val s_idle :: s_regular :: s_extra_bit :: s_extra_pause :: Nil = Enum(4)
  val state = RegInit(s_idle)

  io.data_out.bits := 0.U
  io.data_out.valid := false.B

  when (state === s_idle) {
    // Next-state logic
    when (first_cycle_done && sym_period_counter === 0.U) {state := s_regular}

    // Current state output
    io.data_out.bits := 0.U
    io.data_out.valid := false.B

  } .elsewhen(state === s_regular) {
    // Next-state logic
    when (io.data_out.ready) {
      when (extra_bit === 1.U) {state := s_extra_bit}
      .elsewhen (extra_pause === 1.U) {state := s_extra_pause}
      .otherwise {state := s_idle}
    }
    // Current state output
    io.data_out.bits := recovered_bit
    io.data_out.valid := true.B

  } .elsewhen (state === s_extra_bit) {
    // Next-state logic
    when (io.data_out.ready) {state := s_idle}

    // Current state output
    io.data_out.bits := backup_sum_bit
    io.data_out.valid := true.B

  } .elsewhen (state === s_extra_pause) {
    // Next-state logic
    when (sym_period_counter === 0.U) {state := s_idle}

    // Current state output
    io.data_out.bits := recovered_bit
    io.data_out.valid := false.B

  } .otherwise {
    state := s_idle
    io.data_out.bits := 0.U
    io.data_out.valid := false.B
  }
}