import React, {useEffect, useState} from 'react';
import {
  ActivityIndicator,
  FlatList,
  NativeModules,
  SafeAreaView,
  StatusBar,
  StyleSheet,
  Switch,
  Text,
  View,
} from 'react-native';

type InstalledApp = {
  packageName: string;
  appName: string;
  isProtected: boolean;
};

const {AppLockModule} = NativeModules;

function App(): React.JSX.Element {
  const [apps, setApps] = useState<InstalledApp[]>([]);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    loadApps();
  }, []);

  const loadApps = async () => {
    try {
      const installedApps =
        await AppLockModule.getInstalledApps();

      setApps(installedApps);
    } catch (error) {
      console.error('Failed to load apps:', error);
    } finally {
      setLoading(false);
    }
  };

  const toggleApp = async (app: InstalledApp) => {
    try {
      if (app.isProtected) {
        await AppLockModule.removeProtectedApp(
          app.packageName,
        );
      } else {
        await AppLockModule.addProtectedApp(
          app.packageName,
        );
      }

      setApps(currentApps =>
        currentApps.map(currentApp =>
          currentApp.packageName === app.packageName
            ? {
                ...currentApp,
                isProtected: !currentApp.isProtected,
              }
            : currentApp,
        ),
      );
    } catch (error) {
      console.error('Failed to update protected app:', error);
    }
  };

  const renderApp = ({
    item,
  }: {
    item: InstalledApp;
  }) => {
    return (
      <View style={styles.appRow}>
        <View style={styles.appInfo}>
          <Text style={styles.appName}>
            {item.appName}
          </Text>

          <Text style={styles.packageName}>
            {item.packageName}
          </Text>
        </View>

        <Switch
          value={item.isProtected}
          onValueChange={() => toggleApp(item)}
        />
      </View>
    );
  };

  return (
    <SafeAreaView style={styles.container}>
      <StatusBar barStyle="light-content" />

      <View style={styles.header}>
        <Text style={styles.title}>
          OpenAppLock
        </Text>

        <Text style={styles.subtitle}>
          Lock Apps
        </Text>
      </View>

      {loading ? (
        <View style={styles.loading}>
          <ActivityIndicator size="large" />

          <Text style={styles.loadingText}>
            Loading apps...
          </Text>
        </View>
      ) : (
        <FlatList
          data={apps}
          keyExtractor={item => item.packageName}
          renderItem={renderApp}
          contentContainerStyle={styles.list}
        />
      )}
    </SafeAreaView>
  );
}

const styles = StyleSheet.create({
  container: {
    flex: 1,
    backgroundColor: '#101010',
  },

  header: {
    paddingHorizontal: 20,
    paddingTop: 20,
    paddingBottom: 16,
  },

  title: {
    fontSize: 28,
    fontWeight: '700',
    color: '#ffffff',
  },

  subtitle: {
    marginTop: 4,
    fontSize: 16,
    color: '#aaaaaa',
  },

  list: {
    paddingHorizontal: 16,
    paddingBottom: 20,
  },

  appRow: {
    minHeight: 72,
    marginBottom: 8,
    paddingHorizontal: 16,
    borderRadius: 12,
    backgroundColor: '#1c1c1c',
    flexDirection: 'row',
    alignItems: 'center',
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

  packageName: {
    marginTop: 4,
    fontSize: 12,
    color: '#777777',
  },

  loading: {
    flex: 1,
    alignItems: 'center',
    justifyContent: 'center',
  },

  loadingText: {
    marginTop: 12,
    color: '#aaaaaa',
  },
});

export default App;