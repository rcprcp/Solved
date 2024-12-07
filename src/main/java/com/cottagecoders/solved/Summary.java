package com.cottagecoders.solved;

import java.util.ArrayList;
import java.util.List;

public class Summary {
  List<TicketStartAndEnd> ticketList;
  private Integer softwareCount;
  private Integer cloudCount;
  private Integer autoClosed;
  private String name;
  private long  ID;

  Summary(Integer softwareCount, Integer cloudCount, Integer autoClosed, String name, long ID) {
    this.softwareCount = softwareCount;
    this.cloudCount = cloudCount;
    this.autoClosed = autoClosed;
    this.name = name;
    this.ticketList = new ArrayList<>();
    this.ID = ID;

  }

  public Integer getSoftwareCount() {
    return softwareCount;
  }

  public void incrSoftwareCount() {
    this.softwareCount++;
  }

  public Integer getCloudCount() {
    return cloudCount;
  }

  public Integer getAutoClosedCount() {
    return autoClosed;
  }

  public long getID() {
    return ID;
  }

  public void incrCloudCount() {
    this.cloudCount++;
  }

  public void incrAutoClosedCount() {
    this.autoClosed++;
  }

  public String getName() {
    return name;
  }

  public Integer getAutoClosed() {
    return autoClosed;
  }

  public List<TicketStartAndEnd> getTicketList() {
    return ticketList;
  }
}
