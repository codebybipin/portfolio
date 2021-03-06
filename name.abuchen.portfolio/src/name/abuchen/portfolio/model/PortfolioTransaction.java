package name.abuchen.portfolio.model;

import java.time.LocalDate;
import java.util.ResourceBundle;

import name.abuchen.portfolio.money.CurrencyConverter;
import name.abuchen.portfolio.money.Money;
import name.abuchen.portfolio.money.MoneyCollectors;
import name.abuchen.portfolio.money.Quote;
import name.abuchen.portfolio.money.Values;

public class PortfolioTransaction extends Transaction
{
    public enum Type
    {
        /** Records the purchase of a security. */
        BUY(true),
        /** Records the sale of a security. */
        SELL(false),
        /** Records the transfer of assets from another portfolio. */
        TRANSFER_IN(true),
        /** Records the transfer of assets to another portfolio. */
        TRANSFER_OUT(false),
        /** Records the transfer of assets into the portfolio. */
        DELIVERY_INBOUND(true),
        /** Records the transfer of assets out of a portfolio. */
        DELIVERY_OUTBOUND(false);

        private static final ResourceBundle RESOURCES = ResourceBundle.getBundle("name.abuchen.portfolio.model.labels"); //$NON-NLS-1$

        private final boolean isPurchase;

        private Type(boolean isPurchase)
        {
            this.isPurchase = isPurchase;
        }

        /**
         * True if the transaction is one of the purchase types such as buy,
         * transfer in, or an inbound delivery.
         */
        public boolean isPurchase()
        {
            return isPurchase;
        }

        /**
         * True if the transaction is one of the liquidation types such as sell,
         * transfer out, or an outbound delivery.
         */
        public boolean isLiquidation()
        {
            return !isPurchase;
        }

        @Override
        public String toString()
        {
            return RESOURCES.getString("portfolio." + name()); //$NON-NLS-1$
        }
    }

    private Type type;

    @Deprecated
    /* package */transient long fees;

    @Deprecated
    /* package */transient long taxes;

    public PortfolioTransaction()
    {
        // needed for xstream de-serialization
    }

    public PortfolioTransaction(LocalDate date, String currencyCode, long amount, Security security, long shares,
                    Type type, long fees, long taxes)
    {
        super(date, currencyCode, amount, security, shares, null);
        this.type = type;

        if (fees != 0)
            addUnit(new Unit(Unit.Type.FEE, Money.of(currencyCode, fees)));
        if (taxes != 0)
            addUnit(new Unit(Unit.Type.TAX, Money.of(currencyCode, taxes)));
    }

    public PortfolioTransaction(String date, String currencyCode, long amount, Security security, long shares,
                    Type type, long fees, long taxes)
    {
        this(LocalDate.parse(date), currencyCode, amount, security, shares, type, fees, taxes);
    }

    public Type getType()
    {
        return type;
    }

    public void setType(Type type)
    {
        this.type = type;
    }

    /**
     * Returns the gross value, i.e. the value including taxes and fees. See
     * {@link #getGrossValue()}.
     */
    public long getGrossValueAmount()
    {
        long taxAndFees = getUnits().filter(u -> u.getType() == Unit.Type.TAX || u.getType() == Unit.Type.FEE)
                        .collect(MoneyCollectors.sum(getCurrencyCode(), u -> u.getAmount())).getAmount();

        if (this.type.isPurchase())
            return getAmount() - taxAndFees;
        else
            return getAmount() + taxAndFees;
    }

    /**
     * Returns the gross value, i.e. the value before taxes and fees are
     * applied. In the case of a buy transaction, that are the gross costs, i.e.
     * before adding additional taxes and fees. In the case of sell
     * transactions, that are the gross proceeds before the deduction of taxes
     * and fees.
     */
    public Money getGrossValue()
    {
        return Money.of(getCurrencyCode(), getGrossValueAmount());
    }

    /**
     * Returns the gross price per share, i.e. the gross value divided by the
     * number of shares bought or sold. The quote uses the currency of the
     * transaction.
     */
    public Quote getGrossPricePerShare()
    {
        if (getShares() == 0)
            return Quote.of(getCurrencyCode(), 0);

        double grossPrice = getGrossValueAmount() * Values.Share.factor() * Values.Quote.factorToMoney()
                        / (double) getShares();
        return Quote.of(getCurrencyCode(), Math.round(grossPrice));
    }

    /**
     * Returns the gross price per share in the given currency. Converted is not
     * the price per share but the gross value before dividing by the number of
     * shares in order minimize rounding errors.
     */
    public Quote getGrossPricePerShare(CurrencyConverter converter)
    {
        if (getShares() == 0)
            return Quote.of(converter.getTermCurrency(), 0);

        if (converter.getTermCurrency().equals(getCurrencyCode()))
            return getGrossPricePerShare();

        // Because the gross value is calculated with taxes (which might be in
        // transaction currency and not in security currency) we must convert
        // the gross value (instead of checking the unit type GROSS_VALUE)

        long grossValue = getGrossValue().with(converter.at(getDate())).getAmount();
        double grossPrice = grossValue * Values.Share.factor() * Values.Quote.factorToMoney() / (double) getShares();
        return Quote.of(converter.getTermCurrency(), Math.round(grossPrice));
    }
}
