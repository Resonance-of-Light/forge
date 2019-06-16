package forge.screens.home.online;

import java.net.BindException;
import java.util.ArrayList;
import java.util.List;

import javax.swing.JMenu;
import javax.swing.SwingUtilities;

import forge.FThreads;
import forge.error.BugReporter;
import forge.gui.FNetOverlay;
import forge.gui.SOverlayUtils;
import forge.gui.framework.EDocID;
import forge.gui.framework.ICDoc;
import forge.menus.IMenuProvider;
import forge.menus.MenuUtil;
import forge.net.ChatMessage;
import forge.net.NetConnectUtil;
import forge.screens.home.CHomeUI;
import forge.screens.home.CLobby;
import forge.screens.home.VLobby;
import forge.screens.home.sanctioned.ConstructedGameMenu;
import forge.util.gui.SOptionPane;

public enum CSubmenuOnlineLobby implements ICDoc, IMenuProvider {
    SINGLETON_INSTANCE;

    private CLobby lobby;

    void setLobby(final VLobby lobbyView) {
        lobby = new CLobby(lobbyView);
        initialize();
    }

    void connectToServer() {
        final String url = NetConnectUtil.getServerUrl();
        if (url == null) { return; }

        FThreads.invokeInBackgroundThread(new Runnable() {
            @Override
            public void run() {
                if (url.length() > 0) {
                    join(url);
                }
                else {
                    try {
                        host();
                    } catch (Exception ex) {
                        // IntelliJ swears that BindException isn't thrown in this try block, but it is!
                        if (ex.getClass() == BindException.class) {
                            SOptionPane.showErrorDialog("Unable to start server, port already in use!");
                            SOverlayUtils.hideOverlay();
                        } else {
                            BugReporter.reportException(ex);
                        }
                    }
                }
            }
        });
    }

    private void host() {
        SwingUtilities.invokeLater(new Runnable() {
            @Override
            public void run() {
                SOverlayUtils.startGameOverlay("Starting server...");
                SOverlayUtils.showOverlay();
            }
        });

        final ChatMessage result = NetConnectUtil.host(VSubmenuOnlineLobby.SINGLETON_INSTANCE, FNetOverlay.SINGLETON_INSTANCE);

        SwingUtilities.invokeLater(new Runnable() {
            @Override
            public void run() {
                SOverlayUtils.hideOverlay();
                FNetOverlay.SINGLETON_INSTANCE.show(result);
                if (CHomeUI.SINGLETON_INSTANCE.getCurrentDocID() == EDocID.HOME_NETWORK) {
                    VSubmenuOnlineLobby.SINGLETON_INSTANCE.populate();
                }
                NetConnectUtil.copyHostedServerUrl();
            }
        });
    }

    private void join(final String url) {
        SwingUtilities.invokeLater(new Runnable() {
            @Override
            public void run() {
                SOverlayUtils.startGameOverlay("Connecting to server...");
                SOverlayUtils.showOverlay();
            }
        });

        final ChatMessage result = NetConnectUtil.join(url, VSubmenuOnlineLobby.SINGLETON_INSTANCE, FNetOverlay.SINGLETON_INSTANCE);

        SwingUtilities.invokeLater(new Runnable() {
            @Override
            public void run() {
                SOverlayUtils.hideOverlay();
                if (result instanceof ChatMessage) {
                    FNetOverlay.SINGLETON_INSTANCE.show(result);
                    if (CHomeUI.SINGLETON_INSTANCE.getCurrentDocID() == EDocID.HOME_NETWORK) {
                        VSubmenuOnlineLobby.SINGLETON_INSTANCE.populate();
                    }
                }
            }
        });
    }

    @Override
    public void register() {
    }

    /* (non-Javadoc)
     * @see forge.gui.home.ICSubmenu#update()
     */
    @Override
    public void update() {
        MenuUtil.setMenuProvider(this);
        if (lobby != null) {
            lobby.update();
        }
    }

    /* (non-Javadoc)
     * @see forge.gui.home.ICSubmenu#initialize()
     */
    @Override
    public void initialize() {
        if (lobby != null) {
            lobby.initialize();
        }
    }

    /* (non-Javadoc)
     * @see forge.gui.menubar.IMenuProvider#getMenus()
     */
    @Override
    public List<JMenu> getMenus() {
        final List<JMenu> menus = new ArrayList<JMenu>();
        menus.add(ConstructedGameMenu.getMenu());
        return menus;
    }
}
