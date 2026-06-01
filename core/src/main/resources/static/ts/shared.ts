import '../css/tailwind.css';

import Alpine from '@alpinejs/csp';
import htmx from 'htmx.org';

window.htmx = htmx;

type Theme = 'light' | 'dark';
const DARK: Theme = 'dark';
const LIGHT: Theme = 'light';

class ThemeHelper {
  static getTheme = (): Theme => {
    const match = document.cookie.match(/(?:^|; )theme=(dark|light)/);
    if (match) return match[1] as Theme;
    else
      return window.matchMedia('(prefers-color-scheme: dark)').matches
        ? DARK
        : LIGHT;
  };

  static applyTheme = (theme: Theme) => {
    document.documentElement.dataset.theme = theme;
    document.cookie = `theme=${theme}; path=/; max-age=31536000; SameSite=Lax; Secure`;
  };
}

class AlpineHandler {
  constructor() {
    document.addEventListener('alpine:init', () => {
      this.registerAlertCloser();
      this.registerThemeStore();
      this.registerDeleteModal();
      this.registerDeleteModalStore();
      this.registerToastCloser();
    });
    Alpine.start();
  }

  private registerAlertCloser() {
    Alpine.data('alert', (duration?: number) => ({
      timeoutId: null as ReturnType<typeof setTimeout> | null,
      init() {
        if (typeof duration === 'number') {
          this.timeoutId = setTimeout(() => this.remove(), duration * 1000);
        }
      },
      remove() {
        if (this.timeoutId) {
          clearTimeout(this.timeoutId);
          this.timeoutId = null;
        }
        this.$root.remove();
      },
    }));
  }

  private registerThemeStore() {
    type ThemeStore = {
      value: Theme;
      init(): void;
      toggle(): void;
    };
    const store: ThemeStore = {
      value: LIGHT,
      init() {
        this.value = ThemeHelper.getTheme();
        Alpine.effect(() => {
          ThemeHelper.applyTheme(this.value);
        });
      },
      toggle() {
        this.value = this.value === DARK ? LIGHT : DARK;
      },
    };
    Alpine.store('theme', store);
  }

  private registerDeleteModal() {
    Alpine.data('deleteModal', () => ({
      init() {
        const dialog = this.$el as HTMLDialogElement;
        this.$watch('$store.deleteModal.open', (open: boolean) => {
          if (open) dialog.showModal();
          else dialog.close();
        });
      },
    }));
  }

  private registerDeleteModalStore() {
    type DeleteModalStore = {
      open: boolean;
      id: string;
      details: string;
      baseUrl: string;
      url: string;
      setBaseUrl(url: string): void;
      show(id: string, details: string): void;
      hide(): void;
    };
    const store: DeleteModalStore = {
      open: false,
      id: '',
      details: '',
      baseUrl: '',
      url: '',
      setBaseUrl(url: string) {
        this.baseUrl = `/${url.trim().replace(/^\/+|\/+$/g, '')}/`;
      },
      show(id: string, details: string) {
        this.id = id;
        this.details = details;
        this.open = true;
        this.url = `${this.baseUrl}${this.id}`;
      },
      hide() {
        this.id = '';
        this.details = '';
        this.open = false;
        this.url = '';
      },
    };
    Alpine.store('deleteModal', store);
  }

  private registerToastCloser() {
    Alpine.data('toast', (duration: number = 10) => ({
      init() {
        setTimeout(() => this.$root.remove(), duration * 1000);
      },
    }));
  }
}

new AlpineHandler();
