package de.omegazirkel.risingworld.stargate.dhd;

import de.omegazirkel.risingworld.stargate.network.GateNetworkClient;
import de.omegazirkel.risingworld.stargate.ui.StargatePlayerPluginSettings;
import de.omegazirkel.risingworld.tools.I18n;
import de.omegazirkel.risingworld.tools.ui.AdvancedButton;
import de.omegazirkel.risingworld.tools.ui.AdvancedButtonFactory;
import de.omegazirkel.risingworld.tools.ui.AdvancedBaseButton;
import de.omegazirkel.risingworld.tools.ui.AdvancedButtonState;
import de.omegazirkel.risingworld.tools.ui.BasePluginOverlay;
import net.risingworld.api.ui.UILabel;
import net.risingworld.api.ui.UIElement;
import net.risingworld.api.ui.style.Pivot;
import net.risingworld.api.ui.style.Position;
import net.risingworld.api.ui.style.TextAnchor;
import net.risingworld.api.ui.style.Unit;

/** Uses the standard Tools modal, buttons and lifecycle. */
final class DhdOverlay extends BasePluginOverlay {
    private static final int LOCAL_BORDER = 0x286B43FF;
    private static final int REMOTE_BORDER = 0xD47A22FF;
    private static final int SHARED_BORDER = 0x3979E0FF;
    private final DhdService.Session session;
    private final DhdService service;
    private final GateNetworkClient network;
    private final I18n i18n;
    private final UILabel status;
    private final UILabel selection;
    private final UILabel pageLabel;
    private final UILabel[] chevrons = new UILabel[7];
    static final int PAGE_SIZE = 20;
    private final AdvancedButton[] rows = new AdvancedButton[PAGE_SIZE];
    private final UIElement[] rowFrames = new UIElement[PAGE_SIZE];
    private final UIElement[] underlines = new UIElement[PAGE_SIZE];
    private final AdvancedButton previous;
    private final AdvancedButton next;
    private final AdvancedButton refresh;
    private final AdvancedButton manual;
    private final AdvancedButton dial;
    private final AdvancedButton discover;
    private final AdvancedButton editAlias;
    private final AdvancedButton startGate;
    private final AdvancedButton share;
    private final AdvancedButton shareAll;
    private final AdvancedButton personalName;

    DhdOverlay(DhdService.Session session, DhdService service, GateNetworkClient network, I18n i18n) {
        super(session.player, player -> { });
        this.session = session; this.service = service; this.network = network; this.i18n = i18n;
        rebuild();
        status = label(16, 12, 18);
        status.style.width.set(53, Unit.Percent);
        selection = label(16, 42, 14);
        startGate = button("I", 59, 12, 4, () -> service.toggleStartGate(session), true);
        startGate.style.height.set(26, Unit.Pixel);
        startGate.setVisible(session.player.isAdmin());
        editAlias = button("✎", 85, 12, 4, () -> service.editAlias(session), true);
        editAlias.style.height.set(26, Unit.Pixel);
        editAlias.setVisible(session.player.isAdmin());
        personalName = button("▤", 90, 12, 4, () -> service.editPersonalName(session), true);
        personalName.style.height.set(26, Unit.Pixel);
        refresh = button("↻", 95, 12, 4, () -> service.refreshFromRelay(session), true);
        refresh.style.height.set(26, Unit.Pixel);
        for (int i = 0; i < chevrons.length; i++) {
            UILabel chevron = new UILabel(Integer.toString(i + 1));
            chevron.setPivot(Pivot.UpperLeft);
            chevron.style.position.set(Position.Absolute);
            chevron.style.left.set(64 + i * 3, Unit.Percent);
            chevron.style.top.set(12, Unit.Pixel);
            chevron.style.width.set(2.5f, Unit.Percent);
            chevron.style.height.set(26, Unit.Pixel);
            chevron.setTextAlign(TextAnchor.MiddleCenter);
            chevron.setFontSize(16);
            body.addChild(chevron); chevrons[i] = chevron;
        }
        for (int i = 0; i < rows.length; i++) {
            final int slot = i;
            UIElement frame = new UIElement();
            frame.setPivot(Pivot.UpperLeft);
            frame.style.position.set(Position.Absolute);
            frame.style.left.set(2 + (i % 4) * 24, Unit.Percent);
            frame.style.top.set(96 + (i / 4) * 42, Unit.Pixel);
            frame.style.width.set(22, Unit.Percent);
            frame.style.height.set(36, Unit.Pixel);
            frame.setBackgroundColor(0x201B13D8);
            frame.setBorder(2);
            frame.setClickable(false);
            frame.setPickable(false);
            body.addChild(frame); rowFrames[i] = frame;
            AdvancedButton row = AdvancedButtonFactory.custom(
                    new AdvancedButtonState(AdvancedBaseButton.State.DEFAULT, 0x00000000, 0x00000000,
                            0xF4F0E6FF, 0x00000000, 0xD7AE5533, "", event -> service.select(session, slot)));
            row.setPivot(Pivot.UpperLeft);
            row.style.position.set(Position.Absolute);
            row.style.left.set(2 + (i % 4) * 24, Unit.Percent);
            row.style.top.set(96 + (i / 4) * 42, Unit.Pixel);
            row.style.width.set(22, Unit.Percent);
            row.style.height.set(36, Unit.Pixel);
            row.setBorder(0);
            row.setHoverBorderWidth(0);
            UIElement underline = new UIElement();
            underline.setPivot(Pivot.UpperLeft);
            underline.style.position.set(Position.Absolute);
            underline.style.left.set(16, Unit.Percent);
            underline.style.top.set(31, Unit.Pixel);
            underline.style.width.set(68, Unit.Percent);
            underline.style.height.set(1, Unit.Pixel);
            underline.setBackgroundColor(0xE7D39CFF);
            row.addChild(underline); underlines[i] = underline;
            body.addChild(row); rows[i] = row;
        }
        pageLabel = label(0, 330, 14);
        pageLabel.style.width.set(100, Unit.Percent);
        pageLabel.setTextAlign(TextAnchor.MiddleCenter);
        previous = button("previous", 2, 330, 20, () -> service.page(session, -1));
        next = button("next", 78, 330, 20, () -> service.page(session, 1));
        shareAll = toggleButton("share_all", 2, 380, 18, () -> service.toggleAll(session));
        share = toggleButton("share", 20, 380, 18, () -> service.toggleShare(session));
        manual = button("manual", 38, 380, 18, () -> service.manual(session));
        dial = button("dial", 56, 380, 18, () -> service.dial(session));
        discover = button("discover", 74, 380, 24, () -> service.discover(session));
        update();
    }

    @Override protected I18n t() { return i18n; }
    private String text(String key) { return i18n.get("tc.stargate.dhd." + key, uiPlayer); }
    @Override protected String titleText() {
        String alias = network.gateAlias(session.gateId);
        String gate = network.localAddress(session.gateId);
        String global = network.globalAddress(session.gateId);
        if (global != null) gate += " / " + global;
        if (alias != null) gate = alias.substring(0, Math.min(20, alias.length())) + " · " + gate;
        return text("title").replace("PH_GATE", gate);
    }
    @Override protected String descriptionText() { return text("description"); }
    @Override protected String legendText() { return text("legend"); }
    @Override protected float panelWidthPercent() { return 64f; }

    private UILabel label(int x, int y, int size) {
        UILabel label = new UILabel(""); label.setPivot(Pivot.UpperLeft); label.setPosition(x, y, false);
        label.setFontSize(size); body.addChild(label); return label;
    }

    private AdvancedButton button(String key, int left, int top, int width, Runnable action) {
        return button(key, left, top, width, action, false);
    }

    private AdvancedButton button(String key, int left, int top, int width, Runnable action, boolean literal) {
        AdvancedButton button = AdvancedButtonFactory.defaultButton(literal ? key : text(key), event -> action.run());
        button.setPivot(Pivot.UpperLeft); button.style.position.set(Position.Absolute); button.style.left.set(left, Unit.Percent); button.style.top.set(top, Unit.Pixel);
        button.style.width.set(width, Unit.Percent); button.style.height.set(38, Unit.Pixel); body.addChild(button); return button;
    }

    private AdvancedButton toggleButton(String key, int left, int top, int width, Runnable action) {
        AdvancedButton button = AdvancedButtonFactory.custom(
                new AdvancedButtonState(AdvancedBaseButton.State.DEFAULT, 0x8A6A2DFF, 0x201B13D8,
                        0xF4F0E6FF, 0xD7AE55FF, 0x342915E8, text(key), event -> action.run()),
                new AdvancedButtonState(AdvancedBaseButton.State.EXTRA_1, LOCAL_BORDER, 0x286B43FF,
                        null, LOCAL_BORDER, 0x357D52FF, null, null));
        button.setPivot(Pivot.UpperLeft); button.style.position.set(Position.Absolute); button.style.left.set(left, Unit.Percent); button.style.top.set(top, Unit.Pixel);
        button.style.width.set(width, Unit.Percent); button.style.height.set(38, Unit.Pixel); body.addChild(button); return button;
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
            int index = session.page * PAGE_SIZE + i;
            boolean present = index < session.addresses.size();
            rowFrames[i].setVisible(present);
            rows[i].setVisible(present);
            rows[i].setClickable(present && !session.loading);
            underlines[i].setVisible(present && session.sharedOwn.contains(session.addresses.get(index)));
            if (present) {
                String address = session.addresses.get(index);
                String alias = session.aliases.get(address);
                rowFrames[i].setBorderColor(session.sharedOnly.contains(address) ? SHARED_BORDER
                        : session.localAddresses.contains(address) ? LOCAL_BORDER : REMOTE_BORDER);
                String mode = StargatePlayerPluginSettings.addressMode(session.player);
                String personal = session.personalNames.get(address);
                String label = mode.equals(StargatePlayerPluginSettings.ADDRESS_OWN) && personal != null
                        ? personal : (mode.equals(StargatePlayerPluginSettings.ADDRESS_ALIASES)
                        || mode.equals(StargatePlayerPluginSettings.ADDRESS_OWN)) && alias != null
                        ? alias : network.displayAddress(address, mode.equals(StargatePlayerPluginSettings.ADDRESS_OWN)
                                ? StargatePlayerPluginSettings.ADDRESS_NETWORK : mode);
                label = label.substring(0, Math.min(24, label.length()));
                rows[i].setText(address.equals(session.selected) ? "▸ " + label : label);
            }
        }
        int pages = Math.max(1, (session.addresses.size() + PAGE_SIZE - 1) / PAGE_SIZE);
        pageLabel.setText(text("page").replace("PH_PAGE", Integer.toString(session.page + 1)).replace("PH_TOTAL", Integer.toString(pages)));
        previous.setClickable(!session.loading && session.page > 0);
        next.setClickable(!session.loading && session.page + 1 < pages);
        refresh.setClickable(!session.loading && view.ready());
        manual.setClickable(!session.loading && view.ready() && view.state().equals("IDLE"));
        dial.setClickable(!session.loading && view.ready() && view.state().equals("IDLE") && session.selected != null);
        discover.setClickable(!session.loading && view.ready() && view.state().equals("IDLE"));
        share.setVisible(service.hasFaction(session));
        share.setState(session.selected != null && session.sharedOwn.contains(session.selected)
                ? AdvancedBaseButton.State.EXTRA_1 : AdvancedBaseButton.State.DEFAULT);
        share.setClickable(!session.loading && service.canShare(session));
        shareAll.setVisible(service.hasFaction(session));
        shareAll.setState(service.allShared(session) ? AdvancedBaseButton.State.EXTRA_1 : AdvancedBaseButton.State.DEFAULT);
        shareAll.setClickable(!session.loading && service.hasFaction(session));
        personalName.setClickable(!session.loading && session.selected != null && !session.sharedOnly.contains(session.selected));
        editAlias.setClickable(session.player.isAdmin() && !session.loading);
        startGate.setText(session.startGate ? "I" : "O");
        startGate.setBorderColor(session.startGate ? LOCAL_BORDER : REMOTE_BORDER);
        startGate.setBackgroundColor(session.startGate ? 0x286B43FF : 0x684022FF);
        startGate.setClickable(session.player.isAdmin() && !session.loading);
    }

    @Override protected void close() { service.close(session); }
    void dismiss() { super.close(); }
}
