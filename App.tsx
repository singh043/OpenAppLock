import React, {
  useCallback,
  useEffect,
  useRef,
  useState,
} from 'react';

import {
  ActivityIndicator,
  Alert,
  AppState,
  AppStateStatus,
  BackHandler,
  FlatList,
  NativeModules,
  SafeAreaView,
  StatusBar,
  StyleSheet,
  Switch,
  Text,
  TextInput,
  TouchableOpacity,
  View,
} from 'react-native';

type InstalledApp = {
  packageName: string;
  appName: string;
  isProtected: boolean;
  isNotificationProtected: boolean;
};

type Section =
  | 'home'
  | 'locked'
  | 'notifications'
  | 'lockApps';

type LockType =
  | 'pin'
  | 'pattern'
  | 'password'
  | 'biometric';

const {AppLockModule} = NativeModules;

const IMMEDIATE = 'immediate';
const AFTER_SCREEN_LOCK = 'after_screen_lock';

function App(): React.JSX.Element {

  const [initializing, setInitializing] =
    useState(true);

  const [hasPin, setHasPin] =
    useState<boolean | null>(null);

  const [settingsAuthenticated, setSettingsAuthenticated] =
    useState(false);

  const [createPin, setCreatePin] =
    useState('');

  const [confirmCreatePin, setConfirmCreatePin] =
    useState('');

  const [creatingPin, setCreatingPin] =
    useState(false);

  const [settingsPin, setSettingsPin] =
    useState('');

  const [authenticatingSettings, setAuthenticatingSettings] =
    useState(false);

  const [apps, setApps] =
    useState<InstalledApp[]>([]);

  const [loading, setLoading] =
    useState(false);

  const [accessibilityEnabled, setAccessibilityEnabled] =
    useState<boolean | null>(null);

  const [showLockNotification, setShowLockNotification] =
    useState<boolean | null>(null);

  const [notificationAccessEnabled, setNotificationAccessEnabled] =
    useState<boolean | null>(null);

  const [lockBehavior, setLockBehavior] =
    useState<string | null>(null);

  const [showChangePin, setShowChangePin] =
    useState(false);

  const [showLockTypeScreen, setShowLockTypeScreen] =
    useState(false);

  const [lockType, setLockType] =
    useState<LockType>('pin');

  const [loadingLockType, setLoadingLockType] =
    useState(false);

  const [currentPin, setCurrentPin] =
    useState('');

  const [newPin, setNewPin] =
    useState('');

  const [confirmNewPin, setConfirmNewPin] =
    useState('');

  const [changingPin, setChangingPin] =
    useState(false);

  const [appSearch, setAppSearch] =
    useState('');

  const [section, setSection] =
    useState<Section>('home');

  const [temporarilyUnlockedLockedApps, setTemporarilyUnlockedLockedApps] =
    useState<Set<string>>(
      new Set(),
    );

  const openingSystemSettings =
    useRef(false);

  const lockTypeSetupInProgress =
    useRef<LockType | null>(null);

  const accessibilityCheckTimer =
    useRef<ReturnType<typeof setTimeout> | null>(
      null,
    );

  const previousAppState =
    useRef<AppStateStatus>(
      AppState.currentState,
    );

  const checkAccessibility =
    useCallback(
      async () => {

        try {

          const enabled =
            await AppLockModule
              .isAccessibilityServiceEnabled();

          setAccessibilityEnabled(
            enabled,
          );

          return enabled;

        } catch (error) {

          console.error(
            'Failed to check accessibility:',
            error,
          );

          setAccessibilityEnabled(
            false,
          );

          return false;
        }
      },
      [],
    );

  const loadApps =
    useCallback(
      async () => {

        try {

          setLoading(true);

          const installedApps =
            await AppLockModule
              .getInstalledApps();

          setApps(
            installedApps,
          );

        } catch (error) {

          console.error(
            'Failed to load apps:',
            error,
          );

        } finally {

          setLoading(false);
        }
      },
      [],
    );

  const loadNotificationSetting =
    useCallback(
      async () => {

        try {

          const enabled =
            await AppLockModule
              .getShowLockNotification();

          setShowLockNotification(
            enabled,
          );

        } catch (error) {

          console.error(
            'Failed to load notification setting:',
            error,
          );

          setShowLockNotification(
            true,
          );
        }
      },
      [],
    );

  const checkNotificationAccess =
    useCallback(
      async () => {

        try {

          const enabled =
            await AppLockModule
              .isNotificationListenerEnabled();

          setNotificationAccessEnabled(
            enabled,
          );

        } catch (error) {

          console.error(
            'Failed to check notification access:',
            error,
          );

          setNotificationAccessEnabled(
            false,
          );
        }
      },
      [],
    );

  const loadLockType =
    useCallback(
      async () => {

        try {

          const type =
            await AppLockModule
              .getLockType();

          if (
            type === 'pin' ||
            type === 'pattern' ||
            type === 'password' ||
            type === 'biometric'
          ) {

            setLockType(
              type,
            );

          } else {

            setLockType(
              'pin',
            );
          }

        } catch (error) {

          console.error(
            'Failed to load lock type:',
            error,
          );

          setLockType(
            'pin',
          );
        }
      },
      [],
    );

  const loadLockBehavior =
    useCallback(
      async () => {

        try {

          const behavior =
            await AppLockModule
              .getLockBehavior();

          setLockBehavior(
            behavior,
          );

        } catch (error) {

          console.error(
            'Failed to load lock behavior:',
            error,
          );

          setLockBehavior(
            IMMEDIATE,
          );
        }
      },
      [],
    );

  const loadMainData =
    useCallback(
      async () => {

        await Promise.all([
          loadApps(),
          loadNotificationSetting(),
          checkNotificationAccess(),
          loadLockBehavior(),
          loadLockType(),
        ]);

      },
      [
        loadApps,
        loadNotificationSetting,
        checkNotificationAccess,
        loadLockBehavior,
        loadLockType,
      ],
    );

  const handleLockTypeSetupReturn =
    useCallback(
      async () => {

        const setupType =
          lockTypeSetupInProgress.current;

        if (!setupType) {
          return;
        }

        lockTypeSetupInProgress.current = null;

        try {

          const configured =
            await AppLockModule
              .isLockTypeConfigured(
                setupType,
              );

          if (configured) {

            setShowLockTypeScreen(
              false,
            );

            setLoadingLockType(
              false,
            );

            await loadMainData();

          } else {

            setLoadingLockType(
              false,
            );

            setShowLockTypeScreen(
              true,
            );
          }

        } catch (error) {

          console.error(
            'Failed to check lock type setup:',
            error,
          );

          setLoadingLockType(
            false,
          );

          setShowLockTypeScreen(
            true,
          );
        }
      },
      [loadMainData],
    );

  useEffect(() => {

    let mounted = true;

    const initialize =
      async () => {

        try {

          const [
            pinExists,
            accessibility,
          ] =
            await Promise.all([
              AppLockModule.hasPin(),
              AppLockModule
                .isAccessibilityServiceEnabled(),
            ]);

          if (!mounted) {
            return;
          }

          setHasPin(
            pinExists,
          );

          setAccessibilityEnabled(
            accessibility,
          );

        } catch (error) {

          console.error(
            'Failed to initialize OpenAppLock:',
            error,
          );

        } finally {

          if (mounted) {

            setInitializing(
              false,
            );
          }
        }
      };

    initialize();

    return () => {

      mounted = false;
    };

  }, []);

  const changeSection =
    (
      nextSection: Section,
    ) => {

      if (
        section === 'locked' &&
        nextSection !== 'locked'
      ) {

        setTemporarilyUnlockedLockedApps(
          new Set(),
        );
      }

      setSection(
        nextSection,
      );
    };

  useEffect(() => {

    const handleAppStateChange =
      async (nextState: AppStateStatus) => {

        const previousState =
          previousAppState.current;

        if (
          previousState === 'active' &&
          (
            nextState === 'background' ||
            nextState === 'inactive'
          )
        ) {

          if (
            !openingSystemSettings.current &&
            !lockTypeSetupInProgress.current
          ) {

            setTemporarilyUnlockedLockedApps(
              new Set(),
            );

            setSettingsAuthenticated(
              false,
            );

            setSettingsPin('');

            setShowChangePin(
              false,
            );

            setShowLockTypeScreen(
              false,
            );
          }
        }

        if (
          (
            previousState === 'background' ||
            previousState === 'inactive'
          ) &&
          nextState === 'active'
        ) {

          if (
            lockTypeSetupInProgress.current
          ) {

            await handleLockTypeSetupReturn();

          } else if (
            openingSystemSettings.current
          ) {

            openingSystemSettings.current =
              false;

            if (
              accessibilityCheckTimer.current
            ) {

              clearTimeout(
                accessibilityCheckTimer.current,
              );
            }

            accessibilityCheckTimer.current =
              setTimeout(
                async () => {

                  accessibilityCheckTimer.current =
                    null;

                  const accessibility =
                    await checkAccessibility();

                  if (
                    accessibility &&
                    settingsAuthenticated
                  ) {

                    await loadMainData();
                  }

                },
                700,
              );

          } else {

            setTemporarilyUnlockedLockedApps(
              new Set(),
            );

            setSettingsAuthenticated(
              false,
            );

            setSettingsPin('');

            setShowChangePin(
              false,
            );

            setShowLockTypeScreen(
              false,
            );
          }
        }

        previousAppState.current =
          nextState;
      };

    const subscription =
      AppState.addEventListener(
        'change',
        handleAppStateChange,
      );

    return () => {

      subscription.remove();

      if (
        accessibilityCheckTimer.current
      ) {

        clearTimeout(
          accessibilityCheckTimer.current,
        );

        accessibilityCheckTimer.current =
          null;
      }
    };

  }, [
    checkAccessibility,
    handleLockTypeSetupReturn,
    loadMainData,
    settingsAuthenticated,
  ]);

  useEffect(() => {

    const subscription =
      BackHandler.addEventListener(
        'hardwareBackPress',
        () => {

          if (
            showLockTypeScreen
          ) {

            setShowLockTypeScreen(
              false,
            );

            return true;
          }

          return false;
        },
      );

    return () => {
      subscription.remove();
    };

  }, [
    showLockTypeScreen,
  ]);

  const validatePin =
    (
      pin: string,
    ): string | null => {

      if (
        pin.length !== 4 &&
        pin.length !== 6
      ) {

        return 'PIN must contain 4 or 6 digits.';
      }

      if (
        !/^\d+$/.test(pin)
      ) {

        return 'PIN must contain only digits.';
      }

      return null;
    };

  const handleCreatePin =
    async () => {

      const validationError =
        validatePin(
          createPin,
        );

      if (
        validationError
      ) {

        Alert.alert(
          'Create PIN',
          validationError,
        );

        return;
      }

      if (
        createPin !==
        confirmCreatePin
      ) {

        Alert.alert(
          'Create PIN',
          'PINs do not match.',
        );

        return;
      }

      try {

        setCreatingPin(
          true,
        );

        await AppLockModule
          .createPin(
            createPin,
          );

        setCreatePin('');
        setConfirmCreatePin('');

        setHasPin(
          true,
        );

        setSettingsAuthenticated(
          true,
        );

        await loadMainData();

      } catch (error: any) {

        Alert.alert(
          'Unable to create PIN',
          error?.message ||
            'Unable to create PIN.',
        );

      } finally {

        setCreatingPin(
          false,
        );
      }
    };

  const handleSettingsPin =
    async () => {

      if (
        settingsPin.length === 0
      ) {

        Alert.alert(
          'OpenAppLock',
          'Enter your PIN.',
        );

        return;
      }

      try {

        setAuthenticatingSettings(
          true,
        );

        const valid =
          await AppLockModule
            .verifyPin(
              settingsPin,
            );

        if (!valid) {

          setSettingsPin('');

          Alert.alert(
            'Incorrect PIN',
            'The PIN you entered is incorrect.',
          );

          return;
        }

        setSettingsPin('');

        setSettingsAuthenticated(
          true,
        );

        await loadMainData();

      } catch (error) {

        Alert.alert(
          'Unable to authenticate',
          'Please try again.',
        );

      } finally {

        setAuthenticatingSettings(
          false,
        );
      }
    };

  const openAccessibilitySettings =
    () => {

      openingSystemSettings.current =
        true;

      AppLockModule
        .openAccessibilitySettings();
    };

  const openNotificationAccessSettings =
    () => {

      openingSystemSettings.current =
        true;

      AppLockModule
        .openNotificationAccessSettings();
    };

  const changeLockBehavior =
    async (
      behavior: string,
    ) => {

      try {

        await AppLockModule
          .setLockBehavior(
            behavior,
          );

        setLockBehavior(
          behavior,
        );

      } catch (error) {

        console.error(
          'Failed to change lock behavior:',
          error,
        );
      }
    };

  const changeLockType =
    async (
      type: LockType,
    ) => {

      if (
        type === lockType
      ) {
        return;
      }

      try {

        setLoadingLockType(
          true,
        );

        await AppLockModule
          .setLockType(
            type,
          );

        setLockType(
          type,
        );

        lockTypeSetupInProgress.current =
          type;

        setShowLockTypeScreen(
          false,
        );

        await AppLockModule
          .openLockTypeSetup();

      } catch (error: any) {

        lockTypeSetupInProgress.current =
          null;

        setLoadingLockType(
          false,
        );

        setShowLockTypeScreen(
          true,
        );

        console.error(
          'Failed to start lock type setup:',
          error,
        );

        Alert.alert(
          'Unable to change lock type',
          error?.message ||
            'Unable to open lock type setup.',
        );
      }
    };

  const getLockTypeTitle =
    (
      type: LockType,
    ) => {

      switch (type) {

        case 'pin':
          return 'PIN';

        case 'pattern':
          return 'Pattern';

        case 'password':
          return 'Password';

        case 'biometric':
          return 'Biometric';

        default:
          return 'PIN';
      }
    };

  const toggleNotificationSetting =
    async (
      value: boolean,
    ) => {

      try {

        await AppLockModule
          .setShowLockNotification(
            value,
          );

        setShowLockNotification(
          value,
        );

      } catch (error) {

        console.error(
          'Failed to update notification setting:',
          error,
        );
      }
    };

  const toggleApp =
    async (
      app: InstalledApp,
    ) => {

      try {

        if (
          app.isProtected
        ) {

          await AppLockModule
            .removeProtectedApp(
              app.packageName,
            );

          setApps(
            currentApps =>
              currentApps.map(
                currentApp =>
                  currentApp.packageName ===
                  app.packageName
                    ? {
                        ...currentApp,
                        isProtected:
                          false,
                        isNotificationProtected:
                          false,
                      }
                    : currentApp,
              ),
          );

          setTemporarilyUnlockedLockedApps(
            current =>
              new Set(
                [...current].filter(
                  packageName =>
                    packageName !==
                    app.packageName,
                ),
              ),
          );

        } else {

          await AppLockModule
            .addProtectedApp(
              app.packageName,
            );

          setApps(
            currentApps =>
              currentApps.map(
                currentApp =>
                  currentApp.packageName ===
                  app.packageName
                    ? {
                        ...currentApp,
                        isProtected:
                          true,
                        isNotificationProtected:
                          true,
                      }
                    : currentApp,
              ),
          );

          setTemporarilyUnlockedLockedApps(
            current =>
              new Set(
                [...current].filter(
                  packageName =>
                    packageName !==
                    app.packageName,
                ),
              ),
          );
        }

      } catch (error) {

        console.error(
          'Failed to update protected app:',
          error,
        );
      }
    };

  const toggleAppNotification =
    async (
      app: InstalledApp,
      enabled: boolean,
    ) => {

      if (
        !showLockNotification
      ) {

        return;
      }

      try {

        await AppLockModule
          .setNotificationProtectedApp(
            app.packageName,
            enabled,
          );

        setApps(
          currentApps =>
            currentApps.map(
              currentApp =>
                currentApp.packageName ===
                app.packageName
                  ? {
                      ...currentApp,
                      isNotificationProtected:
                        enabled,
                    }
                  : currentApp,
            ),
        );

      } catch (error) {

        console.error(
          'Failed to update app notification privacy:',
          error,
        );
      }
    };

  const resetChangePinForm =
    () => {

      setCurrentPin('');
      setNewPin('');
      setConfirmNewPin('');
      setShowChangePin(false);
    };

  const handleChangePin =
    async () => {

      if (
        currentPin.length === 0
      ) {

        Alert.alert(
          'Change PIN',
          'Enter your current PIN.',
        );

        return;
      }

      const validationError =
        validatePin(
          newPin,
        );

      if (
        validationError
      ) {

        Alert.alert(
          'Change PIN',
          validationError,
        );

        return;
      }

      if (
        newPin !==
        confirmNewPin
      ) {

        Alert.alert(
          'Change PIN',
          'New PINs do not match.',
        );

        return;
      }

      if (
        currentPin ===
        newPin
      ) {

        Alert.alert(
          'Change PIN',
          'New PIN must be different from the current PIN.',
        );

        return;
      }

      try {

        setChangingPin(
          true,
        );

        await AppLockModule
          .changePin(
            currentPin,
            newPin,
          );

        setCurrentPin('');
        setNewPin('');
        setConfirmNewPin('');
        setShowChangePin(false);

        Alert.alert(
          'Success',
          'PIN changed successfully.',
        );

      } catch (error: any) {

        Alert.alert(
          'Unable to change PIN',
          error?.message ||
            'Current PIN is incorrect.',
        );

      } finally {

        setChangingPin(
          false,
        );
      }
    };

  const lockedApps =
    apps.filter(
      app =>
        app.isProtected,
    );

  const displayedLockedApps =
    apps.filter(
      app =>
        app.isProtected ||
        temporarilyUnlockedLockedApps.has(
          app.packageName,
        ),
    );

  const notificationApps =
    apps.filter(
      app =>
        app.isProtected,
    );

  const normalizedSearch =
    appSearch
      .trim()
      .toLowerCase();

  const filteredApps =
    apps.filter(
      app =>
        normalizedSearch.length === 0 ||
        app.appName
          .toLowerCase()
          .includes(
            normalizedSearch,
          ) ||
        app.packageName
          .toLowerCase()
          .includes(
            normalizedSearch,
          ),
    );

  const renderLockedApp =
    ({
      item,
    }: {
      item: InstalledApp;
    }) => {

      const isTemporarilyUnlocked =
        !item.isProtected &&
        temporarilyUnlockedLockedApps.has(
          item.packageName,
        );

      return (
        <View
          style={styles.appRow}>

          <View
            style={styles.appInfo}>

            <Text
              style={styles.appName}>
              {item.appName}
            </Text>

            <Text
              style={styles.packageName}>
              {item.packageName}
            </Text>

          </View>

          <Switch
            value={
              !isTemporarilyUnlocked
            }
            onValueChange={() =>
              toggleApp(item)
            }
          />

        </View>
      );
    };

  const renderNotificationApp =
    ({
      item,
    }: {
      item: InstalledApp;
    }) => {

      const notificationDisabled =
        showLockNotification === false;

      return (
        <View
          style={[
            styles.appRow,
            notificationDisabled &&
              styles.disabledAppRow,
          ]}>

          <View
            style={styles.appInfo}>

            <Text
              style={[
                styles.appName,
                notificationDisabled &&
                  styles.disabledAppText,
              ]}>
              {item.appName}
            </Text>

            <Text
              style={[
                styles.packageName,
                notificationDisabled &&
                  styles.disabledPackageText,
              ]}>
              {item.packageName}
            </Text>

          </View>

          <Switch
            value={
              item.isNotificationProtected
            }
            disabled={
              notificationDisabled
            }
            onValueChange={
              value =>
                toggleAppNotification(
                  item,
                  value,
                )
            }
          />

        </View>
      );
    };

  const renderLockApp =
    ({
      item,
    }: {
      item: InstalledApp;
    }) => {

      return (
        <View
          style={styles.appRow}>

          <View
            style={styles.appInfo}>

            <Text
              style={styles.appName}>
              {item.appName}
            </Text>

            <Text
              style={styles.packageName}>
              {item.packageName}
            </Text>

          </View>

          <Switch
            value={
              item.isProtected
            }
            onValueChange={() =>
              toggleApp(item)
            }
          />

        </View>
      );
    };

  const renderLockTypeOption =
    (
      type: LockType,
      title: string,
      description: string,
    ) => {

      const selected =
        lockType === type;

      return (
        <TouchableOpacity
          style={styles.lockTypeOption}
          disabled={loadingLockType || selected}
          onPress={() =>
            changeLockType(type)
          }>

          <View
            style={styles.radioOuter}>

            {selected && (
              <View
                style={styles.radioInner}
              />
            )}

          </View>

          <View
            style={styles.optionInfo}>

            <Text
              style={styles.optionTitle}>
              {title}
            </Text>

            <Text
              style={styles.optionDescription}>
              {description}
            </Text>

          </View>

        </TouchableOpacity>
      );
    };

  if (
    initializing
  ) {

    return (
      <SafeAreaView
        style={styles.container}>

        <StatusBar
          barStyle="light-content"
        />

        <View
          style={styles.center}>

          <ActivityIndicator
            size="large"
          />

          <Text
            style={styles.loadingText}>
            Starting OpenAppLock...
          </Text>

        </View>

      </SafeAreaView>
    );
  }

  if (
    accessibilityEnabled === false
  ) {

    return (
      <SafeAreaView
        style={styles.container}>

        <StatusBar
          barStyle="light-content"
        />

        <View
          style={styles.center}>

          <Text
            style={styles.authIcon}>
            ♿
          </Text>

          <Text
            style={styles.authTitle}>
            Enable Accessibility
          </Text>

          <Text
            style={styles.authMessage}>
            OpenAppLock needs Accessibility
            access to detect protected apps.
          </Text>

          <TouchableOpacity
            style={styles.primaryButton}
            onPress={
              openAccessibilitySettings
            }>

            <Text
              style={styles.primaryButtonText}>
              Enable Accessibility
            </Text>

          </TouchableOpacity>

        </View>

      </SafeAreaView>
    );
  }

  if (
    hasPin === false
  ) {

    return (
      <SafeAreaView
        style={styles.container}>

        <View
          style={styles.center}>

          <Text
            style={styles.authIcon}>
            🔐
          </Text>

          <Text
            style={styles.authTitle}>
            Create AppLock PIN
          </Text>

          <Text
            style={styles.authMessage}>
            Create a 4 or 6 digit PIN.
          </Text>

          <TextInput
            style={styles.authInput}
            value={createPin}
            onChangeText={
              setCreatePin
            }
            keyboardType="number-pad"
            secureTextEntry
            maxLength={6}
            placeholder="Create PIN"
            placeholderTextColor="#777"
          />

          <TextInput
            style={styles.authInput}
            value={confirmCreatePin}
            onChangeText={
              setConfirmCreatePin
            }
            keyboardType="number-pad"
            secureTextEntry
            maxLength={6}
            placeholder="Confirm PIN"
            placeholderTextColor="#777"
          />

          <TouchableOpacity
            style={styles.primaryButton}
            onPress={
              handleCreatePin
            }
            disabled={
              creatingPin
            }>

            <Text
              style={styles.primaryButtonText}>
              {creatingPin
                ? 'Creating...'
                : 'Create PIN'}
            </Text>

          </TouchableOpacity>

        </View>

      </SafeAreaView>
    );
  }

  if (
    hasPin === true &&
    !settingsAuthenticated
  ) {

    return (
      <SafeAreaView
        style={styles.container}>

        <View
          style={styles.center}>

          <Text
            style={styles.authIcon}>
            🔐
          </Text>

          <Text
            style={styles.authTitle}>
            OpenAppLock
          </Text>

          <Text
            style={styles.authMessage}>
            Enter your PIN to access settings.
          </Text>

          <TextInput
            style={styles.authInput}
            value={settingsPin}
            onChangeText={
              setSettingsPin
            }
            keyboardType="number-pad"
            secureTextEntry
            maxLength={6}
            placeholder="Enter PIN"
            placeholderTextColor="#777"
          />

          <TouchableOpacity
            style={styles.primaryButton}
            onPress={
              handleSettingsPin
            }
            disabled={
              authenticatingSettings
            }>

            <Text
              style={styles.primaryButtonText}>
              {authenticatingSettings
                ? 'Checking...'
                : 'Unlock'}
            </Text>

          </TouchableOpacity>

        </View>

      </SafeAreaView>
    );
  }

  if (
    showLockTypeScreen
  ) {

    return (
      <SafeAreaView
        style={styles.container}>

        <StatusBar
          barStyle="light-content"
        />

        <View
          style={styles.lockTypeHeader}>

          <TouchableOpacity
            style={styles.backButton}
            onPress={() =>
              setShowLockTypeScreen(false)
            }>

            <Text
              style={styles.backButtonText}>
              ‹
            </Text>

          </TouchableOpacity>

          <Text
            style={styles.lockTypeTitle}>
            Change Lock Type
          </Text>

        </View>

        <FlatList
          data={[]}
          keyExtractor={() =>
            'lock-type'
          }
          renderItem={null}
          contentContainerStyle={
            styles.list
          }
          ListHeaderComponent={
            <View
              style={styles.lockTypeContainer}>

              <Text
                style={styles.lockTypeDescription}>
                Choose how OpenAppLock should
                protect your apps.
              </Text>

              {loadingLockType && (
                <View
                  style={styles.lockTypeLoading}>

                  <ActivityIndicator
                    size="small"
                  />

                  <Text
                    style={styles.loadingText}>
                    Updating lock type...
                  </Text>

                </View>
              )}

              {renderLockTypeOption(
                'pin',
                'PIN',
                'Use your 4 or 6 digit PIN.',
              )}

              {renderLockTypeOption(
                'pattern',
                'Pattern',
                'Unlock using a pattern.',
              )}

              {renderLockTypeOption(
                'password',
                'Password',
                'Unlock using a password.',
              )}

              {renderLockTypeOption(
                'biometric',
                'Biometric',
                'Use fingerprint or face authentication.',
              )}

            </View>
          }
        />

      </SafeAreaView>
    );
  }

  if (
    showLockNotification === null ||
    notificationAccessEnabled === null ||
    lockBehavior === null
  ) {

    return (
      <SafeAreaView
        style={styles.container}>

        <View
          style={styles.center}>

          <ActivityIndicator />

          <Text
            style={styles.loadingText}>
            Loading settings...
          </Text>

        </View>

      </SafeAreaView>
    );
  }

  return (
    <SafeAreaView
      style={styles.container}>

      <StatusBar
        barStyle="light-content"
      />

      <View
        style={styles.header}>

        <Text
          style={styles.title}>
          OpenAppLock
        </Text>

        <Text
          style={styles.subtitle}>
          App protection
        </Text>

      </View>

      <View
        style={styles.tabBar}>

        <TouchableOpacity
          style={[
            styles.tabButton,
            section === 'home' &&
              styles.tabButtonActive,
          ]}
          onPress={() =>
            changeSection('home')
          }>

          <Text
            style={styles.tabText}>
            Home
          </Text>

        </TouchableOpacity>

        <TouchableOpacity
          style={[
            styles.tabButton,
            section === 'locked' &&
              styles.tabButtonActive,
          ]}
          onPress={() =>
            changeSection('locked')
          }>

          <Text
            style={styles.tabText}>
            Locked Apps
          </Text>

        </TouchableOpacity>

        <TouchableOpacity
          style={[
            styles.tabButton,
            section === 'notifications' &&
              styles.tabButtonActive,
          ]}
          onPress={() =>
            changeSection('notifications')
          }>

          <Text
            style={styles.tabText}>
            Notifications
          </Text>

        </TouchableOpacity>

        <TouchableOpacity
          style={[
            styles.tabButton,
            section === 'lockApps' &&
              styles.tabButtonActive,
          ]}
          onPress={() =>
            changeSection('lockApps')
          }>

          <Text
            style={styles.tabText}>
            Lock Apps
          </Text>

        </TouchableOpacity>

      </View>

      {section === 'home' && (

        <FlatList
          data={[]}
          keyExtractor={() =>
            'home'
          }
          renderItem={null}
          contentContainerStyle={
            styles.list
          }
          ListHeaderComponent={
            <View>

              <View
                style={styles.statsRow}>

                <TouchableOpacity
                  style={styles.statCard}
                  onPress={() =>
                    changeSection('locked')
                  }>

                  <Text
                    style={styles.statIcon}>
                    🔒
                  </Text>

                  <Text
                    style={styles.statNumber}>
                    {lockedApps.length}
                  </Text>

                  <Text
                    style={styles.statLabel}>
                    Apps Locked
                  </Text>

                </TouchableOpacity>

                <TouchableOpacity
                  style={styles.statCard}
                  onPress={() =>
                    changeSection('notifications')
                  }>

                  <Text
                    style={styles.statIcon}>
                    🔕
                  </Text>

                  <Text
                    style={styles.statNumber}>
                    {notificationApps.filter(
                      app =>
                        app.isNotificationProtected,
                    ).length}
                  </Text>

                  <Text
                    style={styles.statLabel}>
                    Notifications Hidden
                  </Text>

                </TouchableOpacity>

              </View>

              <View
                style={styles.section}>

                <Text
                  style={styles.sectionTitle}>
                  Lock Behavior
                </Text>

                <TouchableOpacity
                  style={styles.optionRow}
                  onPress={() =>
                    changeLockBehavior(
                      IMMEDIATE,
                    )
                  }>

                  <View
                    style={styles.radioOuter}>

                    {lockBehavior ===
                      IMMEDIATE && (
                      <View
                        style={styles.radioInner}
                      />
                    )}

                  </View>

                  <View
                    style={styles.optionInfo}>

                    <Text
                      style={styles.optionTitle}>
                      Immediately
                    </Text>

                    <Text
                      style={styles.optionDescription}>
                      Lock the app again when
                      you leave it.
                    </Text>

                  </View>

                </TouchableOpacity>

                <TouchableOpacity
                  style={styles.optionRow}
                  onPress={() =>
                    changeLockBehavior(
                      AFTER_SCREEN_LOCK,
                    )
                  }>

                  <View
                    style={styles.radioOuter}>

                    {lockBehavior ===
                      AFTER_SCREEN_LOCK && (
                      <View
                        style={styles.radioInner}
                      />
                    )}

                  </View>

                  <View
                    style={styles.optionInfo}>

                    <Text
                      style={styles.optionTitle}>
                      After phone is locked
                    </Text>

                    <Text
                      style={styles.optionDescription}>
                      Keep the app unlocked until
                      the phone is locked.
                    </Text>

                  </View>

                </TouchableOpacity>

              </View>

              <View
                style={styles.settingRow}>

                <View
                  style={styles.settingInfo}>

                  <Text
                    style={styles.settingTitle}>
                    Notification privacy
                  </Text>

                  <Text
                    style={styles.settingDescription}>
                    Master switch for locked app
                    notification privacy.
                  </Text>

                </View>

                <Switch
                  value={
                    showLockNotification
                  }
                  onValueChange={
                    toggleNotificationSetting
                  }
                />

              </View>

              {showLockNotification &&
                !notificationAccessEnabled && (

                <View
                  style={styles.permissionRow}>

                  <View
                    style={styles.settingInfo}>

                    <Text
                      style={styles.settingTitle}>
                      Notification Access
                    </Text>

                    <Text
                      style={styles.settingDescription}>
                      Required to hide notification
                      content.
                    </Text>

                  </View>

                  <TouchableOpacity
                    style={styles.smallButton}
                    onPress={
                      openNotificationAccessSettings
                    }>

                    <Text
                      style={styles.smallButtonText}>
                      Enable
                    </Text>

                  </TouchableOpacity>

                </View>
              )}

              {showLockNotification &&
                notificationAccessEnabled && (

                <View
                  style={styles.enabledRow}>

                  <Text
                    style={styles.enabledText}>
                    ✓ Notification access enabled
                  </Text>

                </View>
              )}

              <View
                style={styles.securitySection}>

                <Text
                  style={styles.sectionTitle}>
                  Security
                </Text>

                {!showChangePin ? (

                  <TouchableOpacity
                    style={styles.changePinButton}
                    onPress={() =>
                      setShowChangePin(true)
                    }>

                    <Text
                      style={styles.changePinButtonText}>
                      Change {getLockTypeTitle(lockType)}
                    </Text>

                  </TouchableOpacity>

                ) : (

                  <View>

                    <Text
                      style={styles.inputLabel}>
                      Current PIN
                    </Text>

                    <TextInput
                      style={styles.pinInput}
                      value={currentPin}
                      onChangeText={
                        setCurrentPin
                      }
                      keyboardType="number-pad"
                      secureTextEntry
                      maxLength={6}
                    />

                    <Text
                      style={styles.inputLabel}>
                      New PIN
                    </Text>

                    <TextInput
                      style={styles.pinInput}
                      value={newPin}
                      onChangeText={
                        setNewPin
                      }
                      keyboardType="number-pad"
                      secureTextEntry
                      maxLength={6}
                    />

                    <Text
                      style={styles.inputLabel}>
                      Confirm New PIN
                    </Text>

                    <TextInput
                      style={styles.pinInput}
                      value={confirmNewPin}
                      onChangeText={
                        setConfirmNewPin
                      }
                      keyboardType="number-pad"
                      secureTextEntry
                      maxLength={6}
                    />

                    <View
                      style={styles.pinButtonRow}>

                      <TouchableOpacity
                        style={styles.cancelButton}
                        onPress={
                          resetChangePinForm
                        }>

                        <Text
                          style={styles.cancelButtonText}>
                          Cancel
                        </Text>

                      </TouchableOpacity>

                      <TouchableOpacity
                        style={styles.savePinButton}
                        onPress={
                          handleChangePin
                        }>

                        <Text
                          style={styles.savePinButtonText}>
                          {changingPin
                            ? 'Changing...'
                            : 'Change PIN'}
                        </Text>

                      </TouchableOpacity>

                    </View>

                  </View>
                )}

                <TouchableOpacity
                  style={[
                    styles.changePinButton,
                    styles.changeLockTypeButton,
                  ]}
                  onPress={() =>
                    setShowLockTypeScreen(true)
                  }>

                  <Text
                    style={styles.changePinButtonText}>
                    Change Lock Type
                  </Text>

                </TouchableOpacity>

              </View>

            </View>
          }
        />

      )}

      {section === 'locked' && (

        <FlatList
          data={displayedLockedApps}
          keyExtractor={
            item =>
              `locked-${item.packageName}`
          }
          renderItem={
            renderLockedApp
          }
          contentContainerStyle={
            styles.list
          }
          ListHeaderComponent={
            <View
              style={styles.pageHeader}>

              <Text
                style={styles.pageTitle}>
                Locked Apps
              </Text>

              <Text
                style={styles.pageDescription}>
                {lockedApps.length} app
                {lockedApps.length === 1
                  ? ''
                  : 's'} currently locked.
              </Text>

            </View>
          }
          ListEmptyComponent={

            <View
              style={styles.emptyContainer}>

              <Text
                style={styles.emptyTitle}>
                No locked apps
              </Text>

              <Text
                style={styles.emptyText}>
                Go to Lock Apps and select
                apps to protect.
              </Text>

            </View>
          }
        />

      )}

      {section === 'notifications' && (

        <FlatList
          data={notificationApps}
          keyExtractor={
            item =>
              `notification-${item.packageName}`
          }
          renderItem={
            renderNotificationApp
          }
          contentContainerStyle={
            styles.list
          }
          ListHeaderComponent={
            <View
              style={styles.pageHeader}>

              <Text
                style={styles.pageTitle}>
                Hide Notifications
              </Text>

              {!showLockNotification && (

                <View
                  style={styles.notificationDisabledBanner}>

                  <Text
                    style={styles.notificationDisabledTitle}>
                    Notification privacy is disabled
                  </Text>

                  <Text
                    style={styles.notificationDisabledText}>
                    Enable notification privacy from
                    Home to manage these settings.
                  </Text>

                </View>
              )}

              <Text
                style={styles.pageDescription}>
                {notificationApps.filter(
                  app =>
                    app.isNotificationProtected,
                ).length} app
                {notificationApps.filter(
                  app =>
                    app.isNotificationProtected,
                ).length === 1
                  ? ''
                  : 's'} have notification
                privacy enabled.
              </Text>

            </View>
          }
          ListEmptyComponent={

            <View
              style={styles.emptyContainer}>

              <Text
                style={styles.emptyTitle}>
                No hidden notifications
              </Text>

              <Text
                style={styles.emptyText}>
                Enable notification privacy
                for a locked app.
              </Text>

            </View>
          }
        />

      )}

      {section === 'lockApps' && (

        <FlatList
          data={filteredApps}
          keyExtractor={
            item =>
              `all-${item.packageName}`
          }
          renderItem={
            renderLockApp
          }
          contentContainerStyle={
            styles.list
          }
          ListHeaderComponent={
            <View
              style={styles.pageHeader}>

              <Text
                style={styles.pageTitle}>
                Lock Apps
              </Text>

              <Text
                style={styles.pageDescription}>
                Select apps that require your
                AppLock PIN.
              </Text>

              <TextInput
                style={styles.searchInput}
                value={appSearch}
                onChangeText={
                  setAppSearch
                }
                placeholder="🔎 Search apps..."
                placeholderTextColor="#777"
                autoCapitalize="none"
                autoCorrect={false}
              />

              {loading && (

                <View
                  style={styles.inlineLoading}>

                  <ActivityIndicator
                    size="small"
                  />

                  <Text
                    style={styles.loadingText}>
                    Loading apps...
                  </Text>

                </View>
              )}

            </View>
          }
          ListEmptyComponent={

            !loading ? (

              <View
                style={styles.emptyContainer}>

                <Text
                  style={styles.emptyTitle}>
                  No apps found
                </Text>

                <Text
                  style={styles.emptyText}>
                  Try another app name or
                  package name.
                </Text>

              </View>

            ) : null
          }
        />

      )}

    </SafeAreaView>
  );
}

const styles =
  StyleSheet.create({

    container: {
      flex: 1,
      backgroundColor: '#101010',
    },

    header: {
      paddingHorizontal: 20,
      paddingTop: 18,
      paddingBottom: 12,
    },

    title: {
      fontSize: 28,
      fontWeight: '700',
      color: '#ffffff',
    },

    subtitle: {
      marginTop: 4,
      fontSize: 15,
      color: '#999999',
    },

    tabBar: {
      flexDirection: 'row',
      marginHorizontal: 12,
      marginBottom: 8,
      padding: 4,
      borderRadius: 12,
      backgroundColor: '#1c1c1c',
    },

    tabButton: {
      flex: 1,
      paddingVertical: 10,
      borderRadius: 9,
      alignItems: 'center',
    },

    tabButtonActive: {
      backgroundColor: '#ffffff',
    },

    tabText: {
      fontSize: 11,
      fontWeight: '600',
      color: '#aaaaaa',
    },

    statsRow: {
      flexDirection: 'row',
      marginHorizontal: 16,
      marginBottom: 12,
      gap: 10,
    },

    statCard: {
      flex: 1,
      paddingVertical: 20,
      borderRadius: 12,
      backgroundColor: '#1c1c1c',
      alignItems: 'center',
    },

    statIcon: {
      fontSize: 24,
    },

    statNumber: {
      marginTop: 6,
      fontSize: 28,
      fontWeight: '700',
      color: '#ffffff',
    },

    statLabel: {
      marginTop: 3,
      fontSize: 11,
      color: '#888888',
      textAlign: 'center',
    },

    pageHeader: {
      paddingHorizontal: 16,
      paddingTop: 10,
      paddingBottom: 14,
    },

    pageTitle: {
      fontSize: 22,
      fontWeight: '700',
      color: '#ffffff',
    },

    pageDescription: {
      marginTop: 5,
      fontSize: 13,
      color: '#777777',
    },

    section: {
      marginHorizontal: 16,
      marginBottom: 10,
      paddingHorizontal: 16,
      paddingVertical: 14,
      borderRadius: 12,
      backgroundColor: '#1c1c1c',
    },

    securitySection: {
      marginHorizontal: 16,
      marginBottom: 12,
      paddingHorizontal: 16,
      paddingVertical: 14,
      borderRadius: 12,
      backgroundColor: '#1c1c1c',
    },

    sectionTitle: {
      fontSize: 16,
      fontWeight: '700',
      color: '#ffffff',
    },

    optionRow: {
      flexDirection: 'row',
      alignItems: 'center',
      paddingVertical: 10,
    },

    radioOuter: {
      width: 22,
      height: 22,
      borderRadius: 11,
      borderWidth: 2,
      borderColor: '#888888',
      alignItems: 'center',
      justifyContent: 'center',
    },

    radioInner: {
      width: 12,
      height: 12,
      borderRadius: 6,
      backgroundColor: '#ffffff',
    },

    optionInfo: {
      flex: 1,
      marginLeft: 12,
    },

    optionTitle: {
      fontSize: 15,
      fontWeight: '600',
      color: '#ffffff',
    },

    optionDescription: {
      marginTop: 3,
      fontSize: 12,
      lineHeight: 17,
      color: '#888888',
    },

    settingRow: {
      marginHorizontal: 16,
      marginBottom: 8,
      paddingHorizontal: 16,
      paddingVertical: 14,
      borderRadius: 12,
      backgroundColor: '#1c1c1c',
      flexDirection: 'row',
      alignItems: 'center',
    },

    permissionRow: {
      marginHorizontal: 16,
      marginBottom: 12,
      paddingHorizontal: 16,
      paddingVertical: 14,
      borderRadius: 12,
      backgroundColor: '#241f16',
      flexDirection: 'row',
      alignItems: 'center',
    },

    enabledRow: {
      marginHorizontal: 16,
      marginBottom: 12,
      paddingHorizontal: 16,
      paddingVertical: 10,
      borderRadius: 12,
      backgroundColor: '#182218',
    },

    enabledText: {
      fontSize: 13,
      color: '#8fd18f',
    },

    settingInfo: {
      flex: 1,
      paddingRight: 12,
    },

    settingTitle: {
      fontSize: 16,
      fontWeight: '600',
      color: '#ffffff',
    },

    settingDescription: {
      marginTop: 4,
      fontSize: 12,
      lineHeight: 18,
      color: '#888888',
    },

    smallButton: {
      paddingHorizontal: 18,
      paddingVertical: 10,
      borderRadius: 8,
      backgroundColor: '#ffffff',
    },

    smallButtonText: {
      fontSize: 14,
      fontWeight: '600',
      color: '#101010',
    },

    appRow: {
      minHeight: 72,
      marginHorizontal: 16,
      marginBottom: 8,
      paddingHorizontal: 16,
      borderRadius: 12,
      backgroundColor: '#1c1c1c',
      flexDirection: 'row',
      alignItems: 'center',
    },

    disabledAppRow: {
      opacity: 0.45,
    },

    appInfo: {
      flex: 1,
      paddingRight: 12,
    },

    appName: {
      fontSize: 17,
      fontWeight: '600',
      color: '#ffffff',
    },

    disabledAppText: {
      color: '#888888',
    },

    packageName: {
      marginTop: 4,
      fontSize: 12,
      color: '#777777',
    },

    disabledPackageText: {
      color: '#555555',
    },

    searchInput: {
      height: 48,
      marginTop: 14,
      paddingHorizontal: 15,
      borderRadius: 10,
      backgroundColor: '#1c1c1c',
      borderWidth: 1,
      borderColor: '#333333',
      color: '#ffffff',
      fontSize: 15,
    },

    inlineLoading: {
      paddingVertical: 15,
      alignItems: 'center',
    },

    emptyContainer: {
      marginHorizontal: 16,
      marginTop: 20,
      paddingVertical: 30,
      paddingHorizontal: 20,
      borderRadius: 12,
      backgroundColor: '#1c1c1c',
      alignItems: 'center',
    },

    emptyTitle: {
      fontSize: 17,
      fontWeight: '600',
      color: '#ffffff',
    },

    emptyText: {
      marginTop: 7,
      fontSize: 13,
      lineHeight: 19,
      color: '#777777',
      textAlign: 'center',
    },

    notificationDisabledBanner: {
      marginTop: 12,
      paddingHorizontal: 14,
      paddingVertical: 12,
      borderRadius: 10,
      backgroundColor: '#241f16',
    },

    notificationDisabledTitle: {
      fontSize: 14,
      fontWeight: '600',
      color: '#bbbbbb',
    },

    notificationDisabledText: {
      marginTop: 4,
      fontSize: 12,
      lineHeight: 17,
      color: '#777777',
    },

    changePinButton: {
      marginTop: 10,
      paddingVertical: 14,
      borderRadius: 10,
      backgroundColor: '#ffffff',
      alignItems: 'center',
    },

    changePinButtonText: {
      fontSize: 15,
      fontWeight: '600',
      color: '#101010',
    },

    changeLockTypeButton: {
      marginTop: 8,
    },

    lockTypeHeader: {
      minHeight: 64,
      paddingHorizontal: 16,
      flexDirection: 'row',
      alignItems: 'center',
      borderBottomWidth: 1,
      borderBottomColor: '#222222',
    },

    backButton: {
      width: 44,
      height: 44,
      alignItems: 'center',
      justifyContent: 'center',
    },

    backButtonText: {
      fontSize: 30,
      color: '#ffffff',
    },

    lockTypeTitle: {
      marginLeft: 4,
      fontSize: 21,
      fontWeight: '700',
      color: '#ffffff',
    },

    lockTypeContainer: {
      paddingHorizontal: 16,
      paddingTop: 18,
    },

    lockTypeDescription: {
      marginBottom: 12,
      fontSize: 14,
      lineHeight: 20,
      color: '#888888',
    },

    lockTypeOption: {
      minHeight: 72,
      marginBottom: 8,
      paddingHorizontal: 16,
      borderRadius: 12,
      backgroundColor: '#1c1c1c',
      flexDirection: 'row',
      alignItems: 'center',
    },

    lockTypeLoading: {
      paddingVertical: 18,
      alignItems: 'center',
    },

    inputLabel: {
      marginTop: 12,
      marginBottom: 6,
      fontSize: 13,
      color: '#aaaaaa',
    },

    pinInput: {
      height: 50,
      borderRadius: 8,
      paddingHorizontal: 14,
      backgroundColor: '#101010',
      borderWidth: 1,
      borderColor: '#333333',
      color: '#ffffff',
      fontSize: 18,
      textAlign: 'center',
      letterSpacing: 4,
    },

    pinButtonRow: {
      flexDirection: 'row',
      marginTop: 16,
      gap: 10,
    },

    cancelButton: {
      flex: 1,
      paddingVertical: 13,
      borderRadius: 8,
      backgroundColor: '#333333',
      alignItems: 'center',
    },

    cancelButtonText: {
      fontSize: 14,
      fontWeight: '600',
      color: '#ffffff',
    },

    savePinButton: {
      flex: 1,
      paddingVertical: 13,
      borderRadius: 8,
      backgroundColor: '#ffffff',
      alignItems: 'center',
    },

    savePinButtonText: {
      fontSize: 14,
      fontWeight: '600',
      color: '#101010',
    },

    list: {
      paddingBottom: 30,
    },

    center: {
      flex: 1,
      paddingHorizontal: 32,
      alignItems: 'center',
      justifyContent: 'center',
    },

    loadingText: {
      marginTop: 12,
      color: '#aaaaaa',
    },

    authIcon: {
      fontSize: 42,
      marginBottom: 16,
    },

    authTitle: {
      fontSize: 30,
      fontWeight: '700',
      color: '#ffffff',
      textAlign: 'center',
    },

    authMessage: {
      marginTop: 12,
      fontSize: 15,
      lineHeight: 22,
      textAlign: 'center',
      color: '#aaaaaa',
    },

    authInput: {
      width: '100%',
      height: 54,
      marginTop: 16,
      borderRadius: 10,
      paddingHorizontal: 16,
      backgroundColor: '#1c1c1c',
      borderWidth: 1,
      borderColor: '#333333',
      color: '#ffffff',
      fontSize: 20,
      textAlign: 'center',
      letterSpacing: 5,
    },

    primaryButton: {
      width: '100%',
      marginTop: 18,
      paddingVertical: 15,
      borderRadius: 10,
      backgroundColor: '#ffffff',
      alignItems: 'center',
    },

    primaryButtonText: {
      fontSize: 16,
      fontWeight: '600',
      color: '#101010',
    },
  });

export default App;