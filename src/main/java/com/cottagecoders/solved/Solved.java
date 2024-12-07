package com.cottagecoders.solved;

import com.beust.jcommander.JCommander;
import com.beust.jcommander.Parameter;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.zendesk.client.v2.Zendesk;
import org.zendesk.client.v2.model.Audit;
import org.zendesk.client.v2.model.Organization;
import org.zendesk.client.v2.model.Status;
import org.zendesk.client.v2.model.Ticket;
import org.zendesk.client.v2.model.events.ChangeEvent;
import org.zendesk.client.v2.model.events.Event;
import org.zendesk.client.v2.model.events.NotificationEvent;

public class Solved {

  private static final long INTERVAL = 86400 * 1000 * 7;

  @Parameter(
      names = {"-s", "--start"},
      description = "Start date (oldest) yyyy-MM-dd format. Dates are inclusive.")
  private String start = "";

  @Parameter(
      names = {"-e", "--end"},
      description = "End date (most recent). yyyy-MM-dd format. Dates are inclusive.")
  private String end = "";

  @Parameter(
      names = {"-d", "--debug"},
      description = "enable this for debugging")
  private Boolean debug = false;

  private Map<Long, Organization> organizationCache = new HashMap<>();

  public static void main(String[] args) {
    Solved solved = new Solved();
    try (Zendesk zd =
        new Zendesk.Builder(System.getenv("ZENDESK_URL"))
            .setUsername(System.getenv("ZENDESK_EMAIL"))
            .setToken(System.getenv("ZENDESK_TOKEN"))
            .build()) {
      solved.run(args, zd);

    } catch (Exception ex) {
      System.out.println("Exception: " + ex.getMessage());
      ex.printStackTrace();
      System.exit(111);
    }
  }

  private void run(String[] args, Zendesk zd) {
    // process command line args.
    JCommander.newBuilder().addObject(this).build().parse(args);

    // process command line dates..
    SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd");
    Date startDate = null;
    Date endDate = null;

    try {
      startDate = sdf.parse(start);
      endDate = sdf.parse(end);
      if (debug) {
        System.out.println("Dates: " + sdf.format(startDate) + "  and " + sdf.format(endDate));
      }

    } catch (ParseException ex) {
      System.out.println("Parse exception ");
      System.exit(6);
    }

    Map<Long, Summary> userCounts = new HashMap<>();
    int ticketCount = 0;
    int closedTickets = 0;

    long diff = endDate.getTime() - startDate.getTime();
    Date newStartDate = startDate;

    while (newStartDate.getTime() < endDate.getTime() | diff > 0) {
      endDate = new Date(newStartDate.getTime() + INTERVAL);
      diff -= INTERVAL;

      String searchTerm =
          String.format("created>=%s created<%s", sdf.format(newStartDate), sdf.format(endDate));

      System.out.println("searchTerm: " + searchTerm);
      // set this for next time.
      newStartDate = endDate;

      for (Ticket t : zd.getTicketsFromSearch(searchTerm)) {
//        Ticket t = zd.getTicket(130745L) ; {
        // backwards date logic, because we want to include the start and end dates.
        // eg. there is only < or > no <= >=    :)

        // ticket are not in date order, must process all tickets.
        // also note that if we search with the searchTerm, this date-checking code is not needed
        // (and the dates for the search term are NOT inclusive.)
        //      if (t.getCreatedAt().before(startDate) || t.getCreatedAt().after(endDate)) {
        //        continue;
        //      }

        // if (!t.getStatus().equals(Status.CLOSED) && !t.getStatus().equals(Status.SOLVED)) {
        // continue;
        // }

        ++ticketCount; // number of tickets processed.
        if (t.getOrganizationId() == null) {
          System.out.println("ticket " + t.getId() + " has an null organization");
          continue;
        }

        if (t.getAssigneeId() == null) {
          System.out.println("ticket " + t.getId() + " has an null assignee");
          continue;
        }

        if(!t.getStatus().equals(Status.SOLVED) && !t.getStatus().equals(Status.CLOSED) ) {
          continue;     // next ticket.
        }

        // is this assignee in the Map?
        if (userCounts.get(t.getAssigneeId()) == null) {
          // nope, add them.
          userCounts.put(
              t.getAssigneeId(),
              new Summary(0, 0, 0,
                          zd.getUser(t.getAssigneeId()).getName(), t.getAssigneeId()));
          // yes, assignee exists in the map.  increment this assignee's ticket count.
        }

        Summary user = userCounts.get(t.getAssigneeId());

        if (t.getTags().contains("deploy_dremio_cloud")) {
          user.incrCloudCount();
        } else {
          user.incrSoftwareCount();
        }

        // check if this ticket has been autoclosed:
        boolean autoClosed = false;
        Date solvedDate = null;

        for (Audit a : zd.getTicketAudits(t.getId())) {

          List<Event> events = a.getEvents();
          for (Event e : events) {
            if (e instanceof NotificationEvent) {

              // Check for Autoclose - this text appears in the notification:
              if (((NotificationEvent) e).getBody().contains("This request will now be closed")) {
                solvedDate = a.getCreatedAt();
                autoClosed = true;
              }

            } else if(e instanceof ChangeEvent) {

              if (((ChangeEvent) e).getFieldName().equals("status")
                  && ((ChangeEvent) e).getValue().equals("solved")) {
                solvedDate = a.getCreatedAt();
                System.out.println(t.getId() + " solved at  " + solvedDate);
              }
            }
          }
        }

        // this ticket is not solved.
        if(solvedDate == null) {
          continue;
        }

        if(autoClosed) {
          user.incrAutoClosedCount();
        }

        user.getTicketList()
                .add(new TicketStartAndEnd(t.getId(), t.getCreatedAt(), solvedDate));

        userCounts.put(t.getAssigneeId(), user);
      }
    }

    List<Summary> itemList = new ArrayList<>(userCounts.values());
    // sort the Summary objects, ascending by name
    Collections.sort(
        itemList,
        new Comparator<Summary>() {
          public int compare(Summary left, Summary right) {
            // case insensitive sort.
            return left.getName().toUpperCase().compareTo(right.getName().toUpperCase());
          }
        });

    // print alphabetically:
    Collections.sort(
        itemList,
        new Comparator<Summary>() {
          public int compare(Summary left, Summary right) {
            // case insensitive sort.
            return left.getName().toUpperCase().compareTo(right.getName().toUpperCase());
          }
        });
    printThem(itemList);

    // print by ticket count, ascending:
    Collections.sort(
        itemList,
        (left, right) -> {
          Integer l = left.getCloudCount() + left.getSoftwareCount();
          Integer r = right.getCloudCount() + right.getSoftwareCount();

          // descending sort
          return r.compareTo(l);
        });

    System.out.println(
        String.format(
            "\n\nSort by total ticket count: %s - %s", sdf.format(startDate), sdf.format(endDate)));
    printThem(itemList);

    int totalTickets = 0;
    int software = 0;
    int cloud = 0;
    int autoclosed = 0;
    for (Map.Entry<Long, Summary> ent : userCounts.entrySet()) {
      totalTickets += ent.getValue().getCloudCount() + ent.getValue().getSoftwareCount();
      software += ent.getValue().getSoftwareCount();
      cloud += ent.getValue().getCloudCount();
      autoclosed += ent.getValue().getAutoClosedCount();
    }
    System.out.println("Total Tickets:" + totalTickets);
    System.out.println("Total Software tickets " + software);
    System.out.println("Total Cloud Tickets " + cloud);
    System.out.println("Total Autoclosed Tickets " + autoclosed);
  }

  private void printThem(List<Summary> engineers) {

    String title =
        String.format(
            "\n\n%-30s  %10s  %10s  %10s  %10s  %10s  %10s  %15s %30s",
            "name",
            "total",
            "software",
            "cloud",
            "%cloud",
            "autoclosed",
            "%autoclosed",
            "medianttr", "meanttr");
    System.out.println(title);

    for (Summary e : engineers) {
      int pct = 0;
      if (e.getCloudCount() != 0) {
        pct = e.getCloudCount() * 100 / (e.getCloudCount() + e.getSoftwareCount());
      } // to be in the list, someone has at least one ticket

      int acPct = 0;
      if (e.getAutoClosedCount() != 0) {
        acPct = e.getAutoClosedCount() * 100 / (e.getCloudCount() + e.getSoftwareCount());
      } // to be in the list, someone has at least one ticket

      // sort the tickets, within each engineer's Summary object in ascending order.

      Collections.sort(e.ticketList, new TicketStartAndEndComparator());

      // select median ticket.
      TicketStartAndEnd tse = e.ticketList.get(e.ticketList.size() / 2);

      Long meanTimeToResolution = mean(e.ticketList);

      System.out.println(
          String.format(
              "%-30s %10d %10d %10d %10s%% %10d %10s%% %30s  %30s",
              e.getName(),
              e.getSoftwareCount() + e.getCloudCount(),
              e.getSoftwareCount(),
              e.getCloudCount(),
              pct,
              e.getAutoClosedCount(),
              acPct,
              dhm(tse.ticketElapsed),
              dhm(meanTimeToResolution)
          ));
    }
  }

  long mean(List<TicketStartAndEnd> tse) {
    long sum = 0;
    for(TicketStartAndEnd t : tse) {
      sum += t.getTicketElapsed();
    }
    return sum/tse.size();
  }

  /**
   * Days Hours Minutes (dhm)
   *
   * @param elapsed
   * @return
   */
  String dhm(long elapsed) {
    long d, h, m = 0;

    if (elapsed == 0) {
      return "nothing";
    }

    elapsed /= 1000; // convert to seconds.

    d = elapsed / 86_400;
    elapsed = elapsed % 86_400;

    h = elapsed / 3600;
    elapsed = elapsed % 3600;

    m = elapsed / 60;

    return String.format("%d days, %d hours %d min", d, h, m);
  }
}

class TicketStartAndEndComparator implements Comparator<TicketStartAndEnd> {
  @Override
  public int compare(TicketStartAndEnd a, TicketStartAndEnd b) {
    if (a.ticketElapsed < b.ticketElapsed) {
      return -1;
    } else if (a.ticketElapsed > b.ticketElapsed) {
      return 1;
    } else {
      return 0;
    }
  }
}
