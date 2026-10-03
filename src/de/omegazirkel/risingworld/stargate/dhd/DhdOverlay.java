package de.omegazirkel.risingworld.stargate.dhd;

import de.omegazirkel.risingworld.stargate.network.GateNetworkClient;
import de.omegazirkel.risingworld.tools.I18n;
import de.omegazirkel.risingworld.tools.ui.AdvancedButton;
import de.omegazirkel.risingworld.tools.ui.AdvancedButtonFactory;
import de.omegazirkel.risingworld.tools.ui.BasePluginOverlay;
import net.risingworld.api.ui.UILabel;
import net.risingworld.api.ui.style.Pivot;
import net.risingworld.api.ui.style.Position;
import net.risingworld.api.ui.style.TextAnchor;
import net.risingworld.api.ui.style.Unit;

/** Uses the standard Tools modal, buttons and lifecycle. */
final class DhdOverlay extends BasePluginOverlay {
    private static final int LOCAL_BORDER = 0x286B43FF;
    private static final int REMOTE_BORDER = 0xD47A22FF;
    private final DhdService.Session session;
    private final DhdService service;
    private final GateNetworkClient network;
    private final I18n i18n;
    private final UILabel status;
    private final UILabel selection;
    private final UILabel pageLabel;
    private final UILabel[] chevrons = new UILabel[7];
    private final AdvancedButton[] rows = new AdvancedButton[8];
    private final AdvancedButton previous;
    private final AdvancedButton next;
    private final AdvancedButton refresh;
    private final AdvancedButton manual;
    private final AdvancedButton dial;
    private final AdvancedButton discover;

    DhdOverlay(DhdService.Session session, DhdService service, GateNetworkClient network, I18n i18n) {
        super(session.player, player -> { });
        this.session = session; this.service = service; this.network = network; this.i18n = i18n;
        rebuild();
        status = label(16, 12, 18);
        selection = label(16, 42, 14);
        for (int i = 0; i < chevrons.length; i++) {
            UILabel chevron = new UILabel(Integer.toString(i + 1));
            chevron.setPivot(Pivot.UpperLeft);
            chevron.style.position.set(Position.Absolute);
            chevron.style.left.set(3 + i * 13, Unit.Percent);
            chevron.style.top.set(72, Unit.Pixel);
            chevron.setSize(42, 30, false);
            chevron.setTextAlign(TextAnchor.MiddleCenter);
            chevron.setFontSize(18);
            body.addChild(chevron); chevrons[i] = chevron;
        }
        for (int i = 0; i < rows.length; i++) {
            final int slot = i;
            AdvancedButton row = AdvancedButtonFactory.defaultButton("", event -> service.select(session, slot));
            row.setPivot(Pivot.UpperLeft);
            row.style.position.set(Position.Absolute);
            row.style.left.set(i % 2 == 0 ? 2 : 51, Unit.Percent);
            row.style.top.set(116 + (i / 2) * 46, Unit.Pixel);
            row.style.width.set(47, Unit.Percent);
            row.style.height.set(38, Unit.Pixel);
            row.setBorder(2);
            body.addChild(row); rows[i] = row;
        }
        pageLabel = label(16, 310, 14);
        previous = button("previous", 1, () -> service.page(session, -1));
        next = button("next", 21, () -> service.page(session, 1));
        refresh = button("refresh", 41, () -> service.refresh(session));
        manual = button("manual", 61, () -> service.manual(session));
        dial = button("dial", 81, () -> service.dial(session));
        discover = AdvancedButtonFactory.defaultButton(text("discover"), event -> service.discover(session));
        discover.setPivot(Pivot.UpperLeft);
        discover.style.position.set(Position.Absolute);
        discover.style.left.set(2, Unit.Percent);
        discover.style.top.set(400, Unit.Pixel);
        discover.style.width.set(96, Unit.Percent);
        discover.style.height.set(32, Unit.Pixel);
        body.addChild(discover);
        update();
    }

    @Override protected I18n t() { return i18n; }
    private String text(String key) { return i18n.get("tc.stargate.dhd." + key, uiPlayer); }
    @Override protected String titleText() { return text("title").replace("PH_GATE", session.gateId); }
    @Override protected String descriptionText() { return text("description"); }
    @Override protected String legendText() { return text("legend"); }
    @Override protected float panelWidthPercent() { return 64f; }

    private UILabel label(int x, int y, int size) {
        UILabel label = new UILabel(""); label.setPivot(Pivot.UpperLeft); label.setPosition(x, y, false);
        label.setFontSize(size); body.addChild(label); return label;
    }

    private AdvancedButton button(String key, int left, Runnable action) {
        AdvancedButton button = AdvancedButtonFactory.defaultButton(text(key), event -> action.run());
        button.setPivot(Pivot.UpperLeft); button.style.position.set(Position.Absolute); button.style.left.set(left, Unit.Percent); button.style.top.set(356, Unit.Pixel);
        button.style.width.set(18, Unit.Percent); button.style.height.set(38, Unit.Pixel); body.addChild(button); return button;
    }

    void update() {
        GateNetworkClient.GateView view = network.gateView(session.gateId);
        String state = !view.ready() ? "offline" : view.state().equals("OPEN")
                ? (view.direction().equals("INCOMING") ? "open_incoming" : "open_outgoing") : view.state().toLowerCase(java.util.Locale.ROOT);
        status.setText(text("state_" + state) + (view.peerGateId().isEmpty() ? "" : " · " + view.peerGateId()));
        selection.setText(session.loading ? text("loading") : session.failed ? text("load_failed")
                : session.addresses.isEmpty() ? text("empty") : session.selected == null ? text("choose")
                : text("selected").replace("PH_GATE", session.selected));
        for (int i = 0; i < chevrons.length; i++) {
            boolean lit = view.ready() && (view.state().equals("OPEN") || i < view.chevrons());
            chevrons[i].setBackgroundColor(lit ? 0xBD8E36FF : 0x252525FF);
        }
        for (int i = 0; i < rows.length; i++) {
            int index = session.page * 8 + i;
            boolean present = index < session.addresses.size();
            rows[i].setVisible(present);
            rows[i].setClickable(present && !session.loading);
            if (present) {
                String address = session.addresses.get(index);
                rows[i].setBorderColor(session.localAddresses.contains(address) ? LOCAL_BORDER : REMOTE_BORDER);
                rows[i].setText(address.equals(session.selected) ? "▸ " + address : address);
            }
        }
        int pages = Math.max(1, (session.addresses.size() + 7) / 8);
        pageLabel.setText(text("page").replace("PH_PAGE", Integer.toString(session.page + 1)).replace("PH_TOTAL", Integer.toString(pages)));
        previous.setClickable(!session.loading && session.page > 0);
        next.setClickable(!session.loading && session.page + 1 < pages);
        refresh.setClickable(!session.loading && view.ready());
        manual.setClickable(!session.loading && view.ready() && view.state().equals("IDLE"));
        dial.setClickable(!session.loading && view.ready() && view.state().equals("IDLE") && session.selected != null);
        discover.setClickable(!session.loading && view.ready() && view.state().equals("IDLE"));
    }

    @Override protected void close() { service.close(session); }
    void dismiss() { super.close(); }
}
