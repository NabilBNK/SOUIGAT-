from datetime import datetime


def generate_ticket_number(*, departure_datetime: datetime, trip_id: int, order_number: int) -> str:
    """Generate compact human ticket number.

    Format: DDMMYY + trip_suffix(2) + order(2+)
    Example: 2903261703 => 29/03/26, trip#17, order#03.

    Keeps 10 chars when order <= 99.
    """
    date_part = departure_datetime.strftime("%d%m%y")
    trip_suffix = int(trip_id) % 100
    if order_number <= 99:
        order_part = f"{int(order_number):02d}"
    else:
        order_part = str(int(order_number))
    return f"{date_part}{trip_suffix:02d}{order_part}"
