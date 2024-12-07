package com.cottagecoders.solved;

import java.util.Date;

public class TicketStartAndEnd {
  long ticketId;
  long ticketElapsed;

  public TicketStartAndEnd() {

  }

  public TicketStartAndEnd(long ticketId, Date ticketCreated, Date ticketSolved) {
    this.ticketId = ticketId;
    ticketElapsed = ticketSolved.getTime() - ticketCreated.getTime();
  }

  public long getTicketId() {
    return ticketId;
  }

  public long getTicketElapsed() {
    return ticketElapsed;
  }
}
