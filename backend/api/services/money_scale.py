from decimal import Decimal, InvalidOperation


MONEY_SCALE_BASE_UNIT = 'base_unit'
MONEY_SCALE_CENTIMES = 'centimes'
SUPPORTED_MONEY_SCALES = {MONEY_SCALE_BASE_UNIT, MONEY_SCALE_CENTIMES}


def parse_integer_money(raw_value, *, field_name):
    try:
        return int(Decimal(str(raw_value)))
    except (TypeError, ValueError, InvalidOperation) as exc:
        raise ValueError(f'Invalid {field_name}: {raw_value!r}') from exc


def normalize_money_amount(value, *, money_scale):
    if money_scale == MONEY_SCALE_CENTIMES:
        if value % 100 != 0:
            raise ValueError('Legacy centime-scaled amounts must be divisible by 100.')
        return value // 100

    if money_scale not in (None, MONEY_SCALE_BASE_UNIT):
        raise ValueError(f'Unsupported money_scale: {money_scale!r}')

    return value


def looks_like_legacy_mobile_passenger_price(price, trip_base_price):
    return (
        trip_base_price > 0
        and price >= 10_000
        and price % 100 == 0
        and price >= trip_base_price * 10
        and price // 100 >= 100
    )


def looks_like_legacy_mobile_expense_amount(amount, description):
    return (
        amount >= 100_000
        and amount % 100 == 0
        and not (description or '').strip()
    )
