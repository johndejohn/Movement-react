import time
import pytest

from views.base_element import BaseElement, BaseEditBox, BaseButton, BaseText
from views.base_view import BaseView


class ProgressBarIcon(BaseElement):

    def __init__(self, driver):
        super(ProgressBarIcon, self).__init__(driver)
        self.locator = self.Locator.xpath_selector("//android.widget.ProgressBar")


class CloseTabButton(BaseElement):
    def __init__(self, driver, name):
        super(CloseTabButton, self).__init__(driver)
        self.locator = self.Locator.xpath_selector("//*[contains(@text, '%s')]/../../../../*[@content-desc='empty-tab']"
                                                   % name)

class WebLinkEditBox(BaseEditBox):

    def __init__(self, driver):
        super(WebLinkEditBox, self).__init__(driver)
        self.locator = self.Locator.xpath_selector("//android.widget.EditText")


class BackToHomeButton(BaseButton):
    def __init__(self, driver):
        super(BackToHomeButton, self).__init__(driver)
        self.locator = self.Locator.xpath_selector('(//android.view.ViewGroup[@content-desc="icon"])[1]')


class BrowserPreviousPageButton(BaseButton):
    def __init__(self, driver):
        super(BrowserPreviousPageButton, self).__init__(driver)
        self.locator = self.Locator.accessibility_id('previous-page-button')


class BrowserNextPageButton(BaseButton):
    def __init__(self, driver):
        super(BrowserNextPageButton, self).__init__(driver)
        self.locator = self.Locator.accessibility_id('next-page-button')


class BrowserRefreshPageButton(BaseButton):
    def __init__(self, driver):
        super(BrowserRefreshPageButton, self).__init__(driver)
        self.locator = self.Locator.accessibility_id('refresh-page-button')


class WebViewBrowserButton(BaseButton):
    def __init__(self, driver):
        super(WebViewBrowserButton, self).__init__(driver)
        self.locator = self.Locator.text_part_selector('WebView Browser Tester')


class AlwaysButton(BaseButton):
    def __init__(self, driver):
        super(AlwaysButton, self).__init__(driver)
        self.locator = self.Locator.text_part_selector('ALWAYS')


class WebViewMenuButton(BaseButton):
    def __init__(self, driver):
        super(WebViewMenuButton, self).__init__(driver)
        self.locator = self.Locator.accessibility_id('chat-menu-button')


class URLEditBoxLockIcon(BaseButton):

    def __init__(self, driver):
        super(URLEditBoxLockIcon, self).__init__(driver)
        self.locator = self.Locator.xpath_selector('(//android.view.ViewGroup[@content-desc="icon"])[2]')


class PolicySummary(BaseElement):

    def __init__(self, driver):
        super(PolicySummary, self).__init__(driver)
        self.locator = self.Locator.xpath_selector('//*[@content-desc="Policy summary"] | //*[@text="Policy summary"]')


class ShareUrlButton(BaseButton):
    def __init__(self, driver):
        super(ShareUrlButton, self).__init__(driver)
        self.locator = self.Locator.accessibility_id('share')

class GoBackButton(BaseButton):
    def __init__(self, driver):
        super(GoBackButton, self).__init__(driver)
        self.locator = self.Locator.translation_id('browsing-site-blocked-go-back')

class OptionsButton(BaseButton):
    def __init__(self, driver):
        super(OptionsButton, self).__init__(driver)
        self.locator = self.Locator.accessibility_id('browser-options')

class OpenTabsButton(BaseButton):
    def __init__(self, driver):
        super(OpenTabsButton, self).__init__(driver)
        self.locator = self.Locator.accessibility_id('browser-open-tabs')


class AddRemoveFavoritesButton(BaseButton):
    def __init__(self, driver):
        super(AddRemoveFavoritesButton, self).__init__(driver)
        self.locator = self.Locator.accessibility_id('add-remove-fav')

class BookmarkNameInput(BaseEditBox):
    def __init__(self, driver):
        super(BookmarkNameInput, self).__init__(driver)
        self.locator = self.Locator.accessibility_id('bookmark-input')

class SaveBookmarkButton(BaseEditBox):
    def __init__(self, driver):
        super(SaveBookmarkButton, self).__init__(driver)
        self.locator = self.Locator.accessibility_id('save-bookmark')


class CloseAllButton(BaseButton):
    def __init__(self, driver):
        super(CloseAllButton, self).__init__(driver)
        self.locator = self.Locator.accessibility_id('close-all')

class ContinueAnywayButton(BaseButton):
    def __init__(self, driver):
        super(ContinueAnywayButton, self).__init__(driver)
        self.locator = self.Locator.translation_id("continue-anyway")


class BaseWebView(BaseView):

    def __init__(self, driver):
        super(BaseWebView, self).__init__(driver)
        self.driver = driver

        self.progress_bar_icon = ProgressBarIcon(self.driver)

        self.url_edit_box_lock_icon = URLEditBoxLockIcon(self.driver)
        self.policy_summary = PolicySummary(self.driver)
        self.back_to_home_button = BackToHomeButton(self.driver)
        self.browser_previous_page_button = BrowserPreviousPageButton(self.driver)
        self.browser_next_page_button = BrowserNextPageButton(self.driver)

        self.web_view_browser = WebViewBrowserButton(self.driver)
        self.web_view_menu_button = WebViewMenuButton(self.driver)
        self.always_button = AlwaysButton(self.driver)
        self.browser_refresh_page_button = BrowserRefreshPageButton(self.driver)
        self.share_url_button = ShareUrlButton(self.driver)
        self.go_back_button = GoBackButton(self.driver)
        self.options_button = OptionsButton(self.driver)
        self.continue_anyway_button = ContinueAnywayButton(self.driver)
        self.open_tabs_button = OpenTabsButton(self.driver)
        self.close_all_button = CloseAllButton(self.driver)

        # bookmarks management
        self.add_remove_favorites_button = AddRemoveFavoritesButton(self.driver)
        self.bookmark_name_input = BookmarkNameInput(self.driver)
        self.save_bookmark_button = SaveBookmarkButton(self.driver)

    def wait_for_d_aap_to_load(self, wait_time=35):
        counter = 0
        while self.progress_bar_icon.is_element_present(5):
            time.sleep(1)
            counter += 1
            if counter > wait_time:
                self.driver.fail("Page is not loaded during %s seconds" % wait_time)

    def open_in_webview(self):
        if self.web_view_browser.is_element_displayed():
            self.web_view_browser.click()
        if self.always_button.is_element_displayed():
            self.always_button.click()

    def remove_tab(self, name='', clear_all=False):
        self.open_tabs_button.click()
        if clear_all:
            self.close_all_button.click()
        else:
            close_button = CloseTabButton(self.driver, name)
            close_button.scroll_to_element()
            close_button.click()

    def edit_bookmark_name(self, name):
        self.bookmark_name_input.clear()
        self.bookmark_name_input.send_keys(name)
        self.save_bookmark_button.click()

    def add_to_bookmarks(self, name=''):
        self.options_button.click()
        self.add_remove_favorites_button.click()
        if name:
            self.edit_bookmark_name(name)
            bookmark_name = name
        else:
            bookmark_name = self.bookmark_name_input.text
            self.save_bookmark_button.click()
        return bookmark_name

