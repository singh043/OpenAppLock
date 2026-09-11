import React, {
  useCallback,
  useEffect,
  useRef,
  useState,
} from 'react';

import {
  ActivityIndicator,
  PixelRatio,
  GestureResponderEvent,
  Alert,
  AppState,
  AppStateStatus,
  BackHandler,
  FlatList,
  Image,
  NativeModules,
  requireNativeComponent,
  SafeAreaView,
  StatusBar,
  StyleSheet,
  Switch,
  Text,
  TextInput,
  TouchableOpacity,
  View,
} from 'react-native';

import { SvgUri } from 'react-native-svg';

// Keep the SVG files as project assets. Metro loads them as asset resources,
// while SvgUri renders the actual SVG instead of treating the asset number as a React component.
const EYE_OFF_ICON = require('./assets/icons/eye_off_icon.svg');
const EYE_ON_ICON = require('./assets/icons/eye_visible_icon.svg');
const CHEVRON_LEFT_ICON = require('./assets/icons/chevron_left.svg');
const NativeBackArrowView = requireNativeComponent('OpenAppLockBackArrow');

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
  | 'lockApps'
  | 'settings';

type LockType =
  | 'pin'
  | 'pattern'
  | 'password'
  | 'biometric';

const {AppLockModule} = NativeModules;

type SettingsPatternPadProps = {
  pattern: number[];
  onChange: React.Dispatch<React.SetStateAction<number[]>>;
};

function SettingsPatternPad({
  pattern,
  onChange,
}: SettingsPatternPadProps): React.JSX.Element {
  const padSize = 290;
  const spacing = padSize / 4;
  const density = PixelRatio.get();

  // Match the existing native PatternView's canvas-pixel values.
  // React Native dimensions are dp, while the native View drawing values
  // (8f dot radius, 18f selected radius, 7f line width, 65f touch radius)
  // are raw canvas pixels, so convert them to dp here.
  const dotRadius = 8 / density;
  const selectedRadius = 18 / density;
  const lineWidth = 7 / density;
  const touchRadius = 65 / density;
  const selectedBorderWidth = 8 / density;

  const getPoint = (index: number) => ({
    x: spacing + (index % 3) * spacing,
    y: spacing + Math.floor(index / 3) * spacing,
  });

  const selectPointAt = (
    x: number,
    y: number,
    currentPattern: number[] = pattern,
  ) => {
    let closest = -1;
    let closestDistance = Number.POSITIVE_INFINITY;

    for (let index = 0; index < 9; index += 1) {
      if (currentPattern.includes(index)) {
        continue;
      }

      const point = getPoint(index);
      const dx = x - point.x;
      const dy = y - point.y;
      const distance = dx * dx + dy * dy;

      if (distance < closestDistance) {
        closestDistance = distance;
        closest = index;
      }
    }

    if (
      closest >= 0 &&
      closestDistance <= touchRadius * touchRadius
    ) {
      onChange(current =>
        current.includes(closest)
          ? current
          : [...current, closest],
      );
    }
  };

  const getLocalTouch = (
    event: GestureResponderEvent,
  ) => ({
    x: event.nativeEvent.locationX,
    y: event.nativeEvent.locationY,
  });

  return (
    <View
      style={{
        width: 314,
        height: 314,
        marginTop: 8,
        alignSelf: 'center',
        borderRadius: 22,
        padding: 12,
        backgroundColor: '#141414',
        overflow: 'hidden',
      }}>
      <View
        onStartShouldSetResponder={() => true}
        onMoveShouldSetResponder={() => true}
        onResponderGrant={event => {
          const {x, y} = getLocalTouch(event);
          onChange([]);
          selectPointAt(x, y, []);
        }}
        onResponderMove={event => {
          const {x, y} = getLocalTouch(event);
          selectPointAt(x, y);
        }}
        onResponderRelease={() => undefined}
        onResponderTerminate={() => undefined}
        style={{
          width: padSize,
          height: padSize,
          position: 'relative',
          backgroundColor: '#121212',
        }}>
        {pattern.slice(1).map((pointIndex, index) => {
          const from = getPoint(pattern[index]);
          const to = getPoint(pointIndex);
          const dx = to.x - from.x;
          const dy = to.y - from.y;
          const length = Math.sqrt(dx * dx + dy * dy);
          const angle =
            (Math.atan2(dy, dx) * 180) / Math.PI;
          const centerX = (from.x + to.x) / 2;
          const centerY = (from.y + to.y) / 2;

          return (
            <View
              key={`line-${pattern[index]}-${pointIndex}-${index}`}
              pointerEvents="none"
              style={{
                position: 'absolute',
                width: length,
                height: lineWidth,
                left: centerX - length / 2,
                top: centerY - lineWidth / 2,
                borderRadius: lineWidth / 2,
                backgroundColor: '#ffffff',
                transform: [
                  {rotate: `${angle}deg`},
                ],
              }}
            />
          );
        })}

        {Array.from({length: 9}, (_, index) => {
          const point = getPoint(index);
          const selected = pattern.includes(index);

          return (
            <View
              key={index}
              pointerEvents="none"
              style={{
                position: 'absolute',
                width: selected
                  ? selectedRadius * 2
                  : dotRadius * 2,
                height: selected
                  ? selectedRadius * 2
                  : dotRadius * 2,
                left:
                  point.x -
                  (selected
                    ? selectedRadius
                    : dotRadius),
                top:
                  point.y -
                  (selected
                    ? selectedRadius
                    : dotRadius),
                borderRadius: selected
                  ? selectedRadius
                  : dotRadius,
                borderWidth: selected ? selectedBorderWidth : 0,
                borderColor: '#ffffff',
                backgroundColor: selected
                  ? '#121212'
                  : '#787878',
                alignItems: 'center',
                justifyContent: 'center',
              }}>
              {selected && (
                <View
                  style={{
                    width: dotRadius * 2,
                    height: dotRadius * 2,
                    borderRadius: dotRadius,
                    backgroundColor: '#787878',
                  }}
                />
              )}
            </View>
          );
        })}
      </View>
    </View>
  );
}


const IMMEDIATE = 'immediate';
const AFTER_SCREEN_LOCK = 'after_screen_lock';
// Eye icons are rendered from SVG assets (not PNG).
const NAV_ICON_HOME = 'data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAIAAAACACAYAAADDPmHLAAAABmJLR0QA/wD/AP+gvaeTAAAEEUlEQVR4nO3dPYtdVRjF8fWYMIRIYJSAA2mmmWYaq0ljZSrRYqxSDhbBRjBfIZ2dkDLa+AKSCIooEgsTBI2RCOIUNo5gY0AwGlHElwn5W0wuGZJJmDtnn/Ocuc/6fYC9n529YM7NOpcrmZmZmZmZmZmZmZmZmZmZmc0I4ABwFjgHHMyexwYEPAp8wF0XgSPZc9kAgAXgGvf7FjiWPZ/1CFgGftzh8id+Ap7MntN6AJwAbj7k8if+AJ7JntcaAtaAf3dx+RObwIvZc1tHQABngNtTXP52Z4HIPoftATAHvLnHi9/uAnAo+zw2BWAeuNzg8ie+AI5mn8t2AVgEvmt4+RPfA0vZ57OHAFaAn3u4/IlfgKeyz2k7AFaBv3q8/Im/gZPZ57VtgNPArQEuf+I2cCb73OVxt9DJ4iIpC/cXOllcJA2NBxc6WfZtkbTv/pcLWJb0saTF5FHudV3ScxGxnj3INB7JHmAawAlJVzS+y5ekY5I+x0VSP5i+0MniIqkluhc6WVwkdUW7QifLeUZeJI02ocC8pPclPZ09S0dXJD0fETeyB9nJKAMALGrrSX85eZRWNrT1CWEje5B7je5TALAi6SvNzuVL0pKkLxlhkTSqAACrkj6T9ETyKH04KulTRlYkjSYAwGlJ70k6nD1Ljw5JOs+IiqT0ZwDggKRXJb2cPcvAXpP0UkTcyhwiNQDAYUnvSFrNnCPRJ5JORsSfWQOkBQBYkPShpJWsGUZiXVufEK5nbJ4SAMZb6GRJK5IGfwhk3IVOlrQiadAAAGuSLkqaH3LffeKIpI8YuEgaJADcKXQkvSFpbog996mDks4xYJHU+ybAnKTXJa31vdeMuSDphYj4p89Neg3ADBU6WXovknoLwAwWOlk2JD0bET/0sXgvzwB3Cp2r8uW3sCTpal9FUvMAbCt0FlqvXVhvRVLTABQpdLL0UiQ1eQYoXOhkaVYkdQ6AC500TYqkFgG4Jhc6Wb6OiONdFmgRALquYXsXEZ3ucDRvBFkOB6A4B6A4B6A4B6A4B6A4B6A4B6A4B6A4B6A4B6A4B6A4B6A4B6A4B6A4B6A4B6A4B6A4B6C4Wfmxg98lDf1uYmgGvuY+Ky+FPh4RN4fcEHhM0m9D7rkTvxRqnTgAxTkAxTkAxTkAxTkAxTkAxTkAxTkAxTkAxTkAxTkAxTkAxTkAxTkAxTkAxTkAxTkAxTkAxTkAxTkAxTkAxTkAxTkAxTkAxTkAxTkA+1vnr+W1CED69+MK+7XrAi0CcLnBGrY3l7ou0CIAr0jabLCOTec/bf3bd9I5ABHxjaRTkjr/hJnt2qakUxGx3nWhJg+BEfGWpOOS3lWDv0v2QDe09aviKxHxdvYwZmZmZmZmZmZmZmZmZmZmNk7/A6nf9nTz53e7AAAAAElFTkSuQmCC';
const NAV_ICON_LOCKED = 'data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAIAAAACACAYAAADDPmHLAAAABmJLR0QA/wD/AP+gvaeTAAAHp0lEQVR4nO2da6xdRRmGn+9QWrAVKSYaQKBqE29RbCm2KUq0aFATJRDx0loDMSFqJEHEEI0X/OEFEn4RmxBjDARsq8ZLomCrhqCFVFHw8oOIiRYstVah1BZ6P68/ZjXWetqzZu/zzVp7z/ckO+fknG/WvGvm3WvPzJ4LBEEQBEEQBEEQBEEQBEEw/ljXAkoiyYCzgHOB+cDc5l/PAU8BT5jZto7kdcJYG0DSycCbgLcBbwReD8ybJtke4GHgAWAjsMnMDnnqDGYYSedLWiPpKQ3PvyTdJul1Xd9XMA2SlkvaOAOVfjw2SFrW9X0GxyDpHEnrHSv+WNZLOrvr+w4ASVdJ2lWw8o/wjKTVXd9/tUg6VdIdHVT8sXxD0pyuy6MqJJ0h6YGOK/5o7pd0etflMggj1w2U9ELgZ6QuXZ94BHirmT3dtZAcRsoAkuYB9wFLutZyHDYDl5jZc10LactE1wLaImkC+Bb9rXyAZcCdSiOOI8HICJV0I/DVIS+zB9hEelxvAXY2fz8DOA9YRBo5nDtV4gxuMLNbh7xGcARJiyQdGLCBNinpR5LerRatdUlzJF0m6d4m7SDsk/TaEmUz9kiakPTQgBWxSdIFQ+R9oQbvbWxW+tgKhkHS1QMU/gFJ189EBSgZ8FOSDg6g40MzUQbVImm2pMczC323pEsctFwqaU+mlr8ofSMZDILy3/17JV3sqGeF0ud7Dqu89Iw9yv/s/2ABTR/O1PSgt6axRNJrMgv67oLavp2p7ZWltOXS51bqezJidwOf9BIyBdcBz2bEX+ElZFj6bIC3Z8TeZmbb3ZQcQzNvcE1Gknd4aRlLJM1V+27XIXUwOUPSuZIOt9S4X9IppTW2oa9PgEXArJax95vZk55ipsLMniBNHG3DbOB8RzkD01cD5DSafuKmYnruzYh9tZuKIeirARZkxP7aS8QM573AS8Qw9NUAL86I/bObiul5LCP2RW4qhqCvBphu8cbR/NNNxczmfZqbiiHoqwFa6zKzg55CpmF/RmzbRm1R+mqAkcDM1LWGYQkDVE4YoHLCAJUTBqicMEDlhAEqJwxQOWGAygkDVE7nS8OUVtBcBiwHXkL60iTny6BRYhvwePPaDKwzs390KagTAyjNlb8GuB54WRcaesIhYANwq5nd14WA4gaQdAVwM7CwdN49RsDdpEWlRZ8IxQwg6STS6t4bSuU5gjwNXG5mvyiVYREDSHoe8B3gnSXyG3H2AavM7HslMnM3gNJmCeuA93rnNUYcBq40s+97Z1SiG/g5ovJzOYm004j7RFLXJ4CkRcBviPGGQXkUWGxm+7wy8K6YmwvkMc68CrjWMwO3J4CkFcDPva5fETuBhV7bz3lOVLx6gDR7SXPtdxz1t/mkEcK5wIHmf0+S+s59woCzSSOZs0mLR7fy342ojLQR1RLy3njzgY8AX54xpd5ImqW8rdoPSbpJaR/AsUZp2fuDGWUjSb/rWncWkt6ScXOTkt7XteaSSDpF0j2ZJniFhxavBlpO9+UuM1vvpKOXNK36DwB/y0i21EOLlwHOzIi93UlDrzGzXcCXMpKc56HDywBnZcT+3knDKJAz3HuOhwAvA7RtzMnM9jhp6D1mlrO28AUeGmKQpnLCAJUTBqicMEDlhAEqJwxQOWGAygkDVE4YoHLCAJUTBqicXm5d5o2kJaTt6I9sSfso8F0z+213qsYItT9QYbKwrtN04mPm10p6fmFNbXGZM1HNE0DSqcBPgTecIOz9wEslvdlzKnafqKkN8AVOXPlHWAp83llLb6jCAEprEz+ekeTa5okx9lRhAOAi8s4DnkfasGLsqcUAOVPUjlD8GJouqMUA/y6UZuSoxQC/Im8l0WSTZuypwgDNMW/3ZCT5sZn93UtPn6jCAA2fIK0tnI4DpM2rqqAmAzxDu4GvWcAuZy29oSYDLKbd/U40sVVQkwFyDm3q5QFPHtRkgGAKwgCVEwaonDBA5YQBKicMUDlhgMoJA1ROTQbImYBadLJql9RkgJztWLo8kr4oNRngIWB3i7jdTWwVeBmgzdeuACZpjpOG/83IbC9wS4vQW5pYdyTNzghvW6ZZeBlga0bsRU4apuIrpMMrjse6JqYUL8+I3e4hwMsAOTtgftpJw/9hZoeBlc3rl6TNqfc2v68EVjYxpbgyI3abm4qZRnl7BUvSdV1rLo2khZJ2ZZTR5V1rbo3SbuE7Mm5uUtKNSieLjT2SLpC0JaN89kty2SjS88CI20mHQ+bwR+AuYAv9Ow9gJjgTWAG8i7yP341mdqmHIE8DLCZ1p2rqanpxjZl93ePCbpVjZg8Da72uXxF/Be70urj3u/OzpFZ2MDifMbP9Xhd3NYCZbQE+6pnHmLMBcD1Mo9TRsV8DPlYirzHiMWCZme2cNnIIShngZOCbwKoS+Y0BO4CLzexP3hkVaaGb2UFgNfDFEvmNOH8AlpaofCjYRTMzmdlNpCFXl3HtEUfAHcDypu1UhOJ9dDNbSzoSdQ3pqPQgnbB6oZldZWbPlsy4SBvgeEg6nXSy+GrSBk45X4+OOo8APwR+YGadHZzVqQGORtIEaah0QfOzN9pmkO3Na2upOQdBEARBEARBEARBEARBEATwH+l5sKXHHWBwAAAAAElFTkSuQmCC';
const NAV_ICON_HIDE = 'data:image/png;base64,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';
const NAV_ICON_APPS = 'data:image/png;base64,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';
const NAV_ICON_SETTINGS = 'data:image/png;base64,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';
const SECURITY_ICON_SHIELD = 'data:image/png;base64,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';
const STAT_ICON_LOCK = 'data:image/png;base64,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';
const STAT_ICON_BELL = 'data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAIAAAACACAYAAADDPmHLAAAABmJLR0QA/wD/AP+gvaeTAAAL/klEQVR4nO2de3BVxRnAf9+5IShxrAImNwkW8FUs2ofWOlXU6tj6qIPOaFRUEFtxxLYzYl5K63idsaNwoXamU+wo46NYsGUcp4qtj76oWkYr1laxPlBS1NwbEF8lSJPc8/UPgrWFJHsee869l/39mezu9+WeX/aes7tnFxwOh8PhcDgcDofD4XA4HA6Hw+FwOBwOh8PhcFQbEldDmptay7YtF6OcBjIWdD2et1wWdD8ZVwxH/MQigLY2TsTTVcARu0bQO9jQM1dWUoojliNeIguguam19G5Zy+4u/n/DrKCrMNNJUH7URG5h25aLGfbiA+gMJjWiLelJoPPrG+jPHIYwCWU86H4oowHw2I4v7wPvkJEuSqVXZVHPpjTyTJroPUBHdgXKhYbhEusJtLP+8/je14CTgGOAhoBN9ADPIKzG9x+XRZv+HnuSZUB0AdqyjyCcFiCkNQm0tWkK4s9GuACYFHPzG1C5D9+7W3749qsxt50a0QVozy4B5gYMG6sE2t54OmgbcAoxPtkMFQ7kt0gpLws3PW45lnWiC9DZNA3ffyJE6MgSaHv2JGABcGzYNiKyBl+ulcWFP6UUPzLxPAZ2NNyOypwQ4UNJoB3jm6DmVpTzg8e0wi/oH5gnP3qnkHYiQfFiaWVDz1zQe4NX1BlMyq7QnPnTiLZnZ6GZdWV08QEuYFTNOm1rvCTtRIIS30hgCxkmNdwNEuZDWEld8SLJMTBk+7mmMWz1f4owM3yWCaDcQ6lmrtz61kdpp2JCrDdMtiTQeROaqelfBfKF6FkmwrP0D0yvhK+E2O+Y45ZAW5um4PmPAQfGlmQydEHp65Lf/FraiQyHlUemuCTQ1uxn8fg9wQdxhmIA4Q1Uu4DNQO/gz+uAA8CbDHoQkIklmlAA72RZ2P1KLO1ZwNozc2QJMqX5lDKrgaaIqTyH8iAZ73f0eWtH+m7WXNMYevVo0FNBp8fwtfMWnne8LOjeGLEdK1gdNIkowYfAviFD96LchSdLZGHhHyHbAEA7s1MpcRXCbGBMyGZeZHvf8fLjdz+MkosNbI+aRZUgKP2I3EZGb5Kbi5vjbFjbGupBrke4knCTaA+SL54joHHmFRXrAkBiEjyN6uWyqOdFizHQtvrPId5SdkwwBUSukXzh1tiTikAiAoBVCRRkAXWF64cbR4g1YI4aerO3ANcQ7DP8Nx5Hy4LiOkupBSYxAcCKBH2gsyXfsyKm9gKh7dlZwFJgVIBqa6grTpMcvqW0AhHPULAhspISXT2zww0b78J2fM5J6+IDSL74M1TPBfoCVPsK27KX2sopKIn2ADuJoScoodIiiwoPxJlXWLQ92wLch/E/lL7NwKhDy2G4ONEeYCcx9ATr2KfwUJw5RUHyxZWg1weo0UxN/xX2MjInlR5gJ4M9wTKQGSGqjziBlCQKQkf2YZQzDKu8yQfFg+V2+q0mNgKp9AA7kZWUUP11yOot9GaXB5lKtsmO5/uBy4EPDKscyKey59jMyYRUBQBAvG9GqF1eEix8pxv0BuMKyrcspmNEqgJoR/MEdqzajUJZSUDd+NuADUZlhVO1/YCs3YSGJ+UeoDQ9phzKRgLJresD8obFM0jNdJv5jES6ApjfMJlQNhJQ592D6b2AcqbdZIYnNQG0hQxwYszNloUEkuvehugvzUrriZpL7zqk1wNMrJ9K+One4SgLCRDvfsOS+/Ov7BSruQxDegJ43hfNCmqY5/z0Jdh79Gpgu1HZjBh+FvGTngAqhtbLmpAjhqlKILmu7ag8Z1RY/T2wB0AnGxUT+WuEYeOUewJ93rCg2WdhgfQEEBqNyqm/PuLcQXoSiKw3LGj2WVggxa8A9jcrKEWIPIGUjgTqG74XoGPtJjI0aQqwt1E5Tz5eSFlxEogYLgL16uwmMkzktAIjhrFL/v+8OFpREqgaLhTReN5DCEH6k0EhqCgJypyKFACcBHFRsQKAkyAOKloAcBJEpeIFACdBFKpCAHAShKVqBAAnQRiqSgBwEgSl6gQAJ0EQqlIAcBKYUrUCgJPAhKoWAJwEI1H1AoCTYDj2CAHASTAUe4wA4CTYHakIMPg6VCrLoMpUgkadXx/XXoiBSKkHyMwHwxVBxL+VSoISmOa+N/3e90LkEpnEBdBrs5MA880RauiykUciEoj+M0CbV2hr48QQuUQi+R6gxA0weFjTSAhr5ZaeN2ylYlsCyW96HWGtYXujkQCvlsdEogLoNc2HAeb7AvlY/0Cs9wSqOfNkmKUdjYeHyCM0yfYAXukHGO+yqU/JouLDVvMZxKYEku9ZhWB6pEwG1RtD5BCaxATQ1sajEc41riASYNOl6FjtCVS+H6Ct87S96agQOYQiuR7A05sw3ZRKeVQWFv9gN6FdsSWB5AtPgD5mmgbi3xQifigSEUA7m6YBp5sWD7TPTsxY6wl8bz6mG0UrZ2hb9qsh4gcmmR7AD2C0cr8s6nnaYjYjYkMCWVxYi2K+saVoIr2AdQG0LfsNzDeCKqH27/xNsNITKNeD6RF5cry2ZuPcQme32O8BhHnGZZV7ZXHxJYvZBCJuCWRx8SUU87Y8vTpE3EBYFUC/O3ZfzP/7+6ghZzGdUMTfE+iNGG8uLSdrrinsKSVG2J3ZGj3q8AAxlkPtB3rtpw1fG0+Y/oF5ZPzaEAdWttDbWKvX6Ry0dgB4n4G+FQgmO4aPopcpgNlOIyGwK0DG2w/f+ISU2ZT6ZlvMJhoeEQ570bMZ4OyP//GD7NCspf3CRjXB8j2A9I5cxjEsnmy12rzNxvH8VyizQ5IqDMWrtXrwpFUB5ObiZgTTjZIcu/IXuWXjezYDJDEQtDSBGNWJivXPzr4AY8YtBSnr83PLlJfZZ+w9toMkc25gR/bLKKuBvZKIVwV8hC8nyOKC6WKS0CQyFyALi8+gej6Q+iFJFcA2fM5N4uJDgtPBsqjnIcgcF2D3zD0P0b/h+cfJ4uJvkgqZ7LmB+befp67naJDzQB8ANiUZv+zx5TX23pToqaKpnhoGoG0NddSMrk07j1jpH8iQ8X8SYtgYEj4NLXUBqpWIh2MmJoETwCKVIEHFC6CdTdPw/YtADgF9F+FRxoz7+eDhTfHFyU2tZduWi1FOAxkLuh7PWy4Lup8ctl4LGSY1LgMNcTimrKCrMFNWmi4iCRHBVsO20RYyTG64DZU5u/n1i/hyliwuBHkzZ+hYrY0T8XQVcMQuvxS9gw09c4e7SOUsQeW+HTy54cYhLj7AEXi6SnNTI99cam5q7ZAXH0BlDpMbhl3Lv2NRSWEmSIiTznUGkxqXDR6yFTsVKYBe1zwOldYRih3B1nevihxs67vfYaiL/3FC0qrXNY8brki5SlCRAlDyT8JkWFn0Rm0/4NCwYbS1acrgEq6R2Gswp+HTKUMJKlMA3zddNrYvklmlV48PvBeBzpvQjPgPIewTZ07lJkFlCoC8blxUOYxRNWu0reFY4yqd9cdRM7AG4RAbOZWTBJUpwIfFp4CNAWpMROQpbcveqZ0NRw5VSDsbjtS27N343hPAgQHa3ziYkzHRJWhYErzebvKIo5E00I7sZSh3hqvMejyeRXkLAGECyjHAwaHaE5klCwvLQqUS5RHR804YaRxiJCpXgBweW7OrEaalnMofyRdPkShrhsNKoCyRRcVvh40LlfoVAEgOH/FnY3pKtx3eI8NlUS4+RPg6EA6KEhcqWAAY3IIFvQTj9+1iZQDkIrml2BVHY+EkkMgLRitaABjcgQO9kmSXn/sIl0u+8EicjQaXwHjPgSGpeAEAJN+zFJFLMX7nLhJ9qM6UhUUrCzYDSPACdeOWR44XtYFyQtsbTwB/BUizpRBv4vkXyoJNf7bU/scMTiUvAdndlnov4HlnyYLuII/Cu6WqBADQeRPGUjOwGLiU+P4+H5G78Ea1235R4//RzqZplPwZiB4C3hbQx6gbtzyu6e6qE2An2tH4JdAcypmE/zt94GHwcpLvtvaGbppUrQA72TEZ5M0CORsYchTwk1WAFxB+hZaWSX5zVb/UUvUCfBJta6hHvKPA/wxIE+jgq9fyPmg3nvcynj4nNxc3p5upw+FwOBwOh8PhcDgcDofD4XA4HA6HwxED/wGSTiKNkMbtVQAAAABJRU5ErkJggg==';
const SECURITY_ICON_LOCK = 'data:image/png;base64,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';


type NavIconName =
  | 'home'
  | 'locked'
  | 'hide'
  | 'apps'
  | 'settings';

function NavIcon({
  name,
  active,
}: {
  name: NavIconName;
  active: boolean;
}): React.JSX.Element {
  const sources: Record<NavIconName, string> = {
    home: NAV_ICON_HOME,
    locked: NAV_ICON_LOCKED,
    hide: NAV_ICON_HIDE,
    apps: NAV_ICON_APPS,
    settings: NAV_ICON_SETTINGS,
  };

  return (
    <Image
      source={{uri: sources[name]}}
      style={styles.bottomNavImage}
      tintColor={active ? '#ff7518' : '#b7b3bc'}
    />
  );
}

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

  const [settingsPassword, setSettingsPassword] =
    useState('');

  const [settingsPattern, setSettingsPattern] =
    useState<number[]>([]);

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

  const [faceUnlockEnabled, setFaceUnlockEnabled] =
    useState(false);

  const [fingerprintUnlockEnabled, setFingerprintUnlockEnabled] =
    useState(false);

  const lockTypeBeforeSetup =
    useRef<LockType | null>(null);

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

  const [showCurrentPin, setShowCurrentPin] =
    useState(false);

  const [showNewPin, setShowNewPin] =
    useState(false);

  const [showConfirmNewPin, setShowConfirmNewPin] =
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
            type === 'password'
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

  const loadBiometricSettings =
    useCallback(
      async () => {

        try {

          const settings =
            await AppLockModule
              .getBiometricSettings();

          setFaceUnlockEnabled(
            Boolean(
              settings?.face,
            ),
          );

          setFingerprintUnlockEnabled(
            Boolean(
              settings?.fingerprint,
            ),
          );

        } catch (error) {

          console.error(
            'Failed to load biometric settings:',
            error,
          );

          setFaceUnlockEnabled(
            false,
          );

          setFingerprintUnlockEnabled(
            false,
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
          loadBiometricSettings(),
        ]);

      },
      [
        loadApps,
        loadNotificationSetting,
        checkNotificationAccess,
        loadLockBehavior,
        loadLockType,
        loadBiometricSettings,
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

            lockTypeBeforeSetup.current =
              null;

            setShowLockTypeScreen(
              false,
            );

            setLoadingLockType(
              false,
            );

            await loadMainData();

          } else {

            const previousType =
              lockTypeBeforeSetup.current;

            if (
              previousType &&
              previousType !== setupType
            ) {
              try {
                await AppLockModule
                  .setLockType(
                    previousType,
                  );
                setLockType(
                  previousType,
                );
              } catch (restoreError) {
                console.error(
                  'Failed to restore previous lock type:',
                  restoreError,
                );
              }
            }

            lockTypeBeforeSetup.current =
              null;

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
            selectedLockType,
          ] =
            await Promise.all([
              AppLockModule.hasPin(),
              AppLockModule
                .isAccessibilityServiceEnabled(),
              AppLockModule.getLockType(),
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

          if (
            selectedLockType === 'pin' ||
            selectedLockType === 'pattern' ||
            selectedLockType === 'password'
          ) {
            setLockType(
              selectedLockType,
            );
          }

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
            setSettingsPassword('');
            setSettingsPattern([]);

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
            setSettingsPassword('');
            setSettingsPattern([]);

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
            showChangePin
          ) {

            resetChangePinForm();

            return true;
          }

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
    showChangePin,
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

  const handleSettingsCredential =
    async () => {

      let credential = '';

      if (lockType === 'pin') {
        credential = settingsPin;
      } else if (lockType === 'password') {
        credential = settingsPassword;
      } else {
        credential = settingsPattern.join('');
      }

      if (credential.length === 0) {
        Alert.alert(
          'OpenAppLock',
          `Enter your ${lockType}.`,
        );

        return;
      }

      if (
        lockType === 'pattern' &&
        settingsPattern.length < 4
      ) {
        Alert.alert(
          'OpenAppLock',
          'Pattern must contain at least 4 points.',
        );

        return;
      }

      try {
        setAuthenticatingSettings(
          true,
        );

        const valid =
          await AppLockModule
            .verifyCredential(
              lockType,
              credential,
            );

        if (!valid) {
          setSettingsPin('');
          setSettingsPassword('');
          setSettingsPattern([]);

          Alert.alert(
            'Incorrect credential',
            `The ${lockType} you entered is incorrect.`,
          );

          return;
        }

        setSettingsPin('');
        setSettingsPassword('');
        setSettingsPattern([]);
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

        lockTypeBeforeSetup.current =
          lockType;

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

  const toggleFaceUnlock =
    async (
      value: boolean,
    ) => {

      try {

        await AppLockModule
          .setFaceUnlockEnabled(
            value,
          );

        setFaceUnlockEnabled(
          value,
        );

      } catch (error) {

        console.error(
          'Failed to update face unlock setting:',
          error,
        );
      }
    };

  const toggleFingerprintUnlock =
    async (
      value: boolean,
    ) => {

      try {

        await AppLockModule
          .setFingerprintUnlockEnabled(
            value,
          );

        setFingerprintUnlockEnabled(
          value,
        );

      } catch (error) {

        console.error(
          'Failed to update fingerprint unlock setting:',
          error,
        );
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
      setShowCurrentPin(false);
      setShowNewPin(false);
      setShowConfirmNewPin(false);
      setShowChangePin(false);
    };

  const openChangePinScreen =
    () => {

      setCurrentPin('');
      setNewPin('');
      setConfirmNewPin('');
      setShowCurrentPin(false);
      setShowNewPin(false);
      setShowConfirmNewPin(false);
      setShowChangePin(true);
    };

  const handleChangePin =
    async () => {

      if (
        currentPin.length === 0 ||
        newPin.length === 0 ||
        confirmNewPin.length === 0
      ) {

        Alert.alert(
          'Change PIN',
          'All fields are required.',
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
          'New PIN and Confirm PIN do not match.',
        );

        return;
      }

      if (
        currentPin ===
        newPin
      ) {

        Alert.alert(
          'Change PIN',
          'New PIN should be different from the current PIN.',
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
        setShowCurrentPin(false);
        setShowNewPin(false);
        setShowConfirmNewPin(false);
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

  const handleChangeCredentialType =
    async (
      type: LockType,
    ) => {

      try {

        setLoadingLockType(
          true,
        );

        await AppLockModule
          .setLockType(
            type,
          );

        lockTypeSetupInProgress.current =
          type;

        setShowChangePin(
          false,
        );

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

        Alert.alert(
          type === 'pattern'
            ? 'Unable to change pattern'
            : 'Unable to change password',
          error?.message ||
            'Unable to open credential setup.',
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
          style={[
            styles.lockTypeOption,
            selected &&
              styles.lockTypeOptionCurrent,
          ]}
          disabled={
            loadingLockType || selected
          }
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
    const settingsAuthMessage =
      lockType === 'pattern'
        ? 'Use your current pattern to access settings.'
        : lockType === 'password'
          ? 'Enter your current password to access settings.'
          : 'Enter your current PIN to access settings.';

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
            {settingsAuthMessage}
          </Text>

          {lockType === 'pattern' ? (
            <SettingsPatternPad
              pattern={settingsPattern}
              onChange={setSettingsPattern}
            />
          ) : (
            <TextInput
              style={styles.authInput}
              value={
                lockType === 'password'
                  ? settingsPassword
                  : settingsPin
              }
              onChangeText={
                lockType === 'password'
                  ? setSettingsPassword
                  : setSettingsPin
              }
              keyboardType={
                lockType === 'password'
                  ? 'default'
                  : 'number-pad'
              }
              secureTextEntry
              maxLength={
                lockType === 'password'
                  ? 64
                  : 6
              }
              placeholder={
                lockType === 'password'
                  ? 'Enter password'
                  : 'Enter PIN'
              }
              placeholderTextColor="#777"
            />
          )}

          <TouchableOpacity
            style={styles.primaryButton}
            onPress={
              handleSettingsCredential
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

            <NativeBackArrowView
              style={{
                width: 48,
                height: 48,
              }}
            />

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

              <Text
                style={styles.biometricSectionTitle}>
                Biometric
              </Text>

              <View
                style={styles.biometricSection}>

                <View
                  style={styles.biometricRow}>

                  <View
                    style={styles.biometricInfo}>

                    <Text
                      style={styles.biometricTitle}>
                      Face
                    </Text>

                    <Text
                      style={styles.biometricDescription}>
                      Unlock using face recognition.
                    </Text>

                  </View>

                  <View
                    style={styles.biometricDivider} />

                  <Switch
                    value={
                      faceUnlockEnabled
                    }
                    disabled={
                      loadingLockType
                    }
                    onValueChange={
                      toggleFaceUnlock
                    }
                  />

                </View>

                <View
                  style={styles.biometricSeparator} />

                <View
                  style={styles.biometricRow}>

                  <View
                    style={styles.biometricInfo}>

                    <Text
                      style={styles.biometricTitle}>
                      Fingerprint
                    </Text>

                    <Text
                      style={styles.biometricDescription}>
                      Unlock using fingerprint.
                    </Text>

                  </View>

                  <View
                    style={styles.biometricDivider} />

                  <Switch
                    value={
                      fingerprintUnlockEnabled
                    }
                    disabled={
                      loadingLockType
                    }
                    onValueChange={
                      toggleFingerprintUnlock
                    }
                  />

                </View>

              </View>

            </View>
          }
        />

      </SafeAreaView>
    );
  }

  if (
    showChangePin
  ) {

    return (
      <SafeAreaView
        style={styles.container}>

        <StatusBar
          barStyle="light-content"
        />

        <View
          style={styles.changePinScreen}>

          <TouchableOpacity
            style={styles.changePinBackButton}
            onPress={resetChangePinForm}>

            <NativeBackArrowView
              style={{
                width: 48,
                height: 48,
              }}
            />

          </TouchableOpacity>

          <View
            style={styles.changePinHeader}>

            <Text
              style={styles.changePinScreenTitle}>
              Change PIN
            </Text>

            <View
              style={styles.changePinHeaderSpacer} />

          </View>

          <View
            style={styles.changePinCard}>

            <Text
              style={styles.inputLabel}>
              Current PIN
            </Text>

            <View
              style={styles.pinInputWrapper}>

              <TextInput
                style={styles.changePinInput}
                value={currentPin}
                onChangeText={setCurrentPin}
                keyboardType="number-pad"
                secureTextEntry={!showCurrentPin}
                maxLength={6}
                placeholder="Enter current PIN"
                placeholderTextColor="#777777"
              />

              <TouchableOpacity
                style={styles.pinVisibilityButton}
                onPress={() =>
                  setShowCurrentPin(
                    value => !value,
                  )
                }
                accessibilityRole="button"
                accessibilityLabel="Toggle current PIN visibility">

                {!showCurrentPin ? (
                  <SvgUri
                    uri={Image.resolveAssetSource(EYE_OFF_ICON).uri}
                    width={26}
                    height={24}
                  />
                ) : (
                  <SvgUri
                    uri={Image.resolveAssetSource(EYE_ON_ICON).uri}
                    width={32}
                    height={24}
                  />
                )}

              </TouchableOpacity>

            </View>

            <Text
              style={styles.inputLabel}>
              New PIN
            </Text>

            <View
              style={styles.pinInputWrapper}>

              <TextInput
                style={styles.changePinInput}
                value={newPin}
                onChangeText={setNewPin}
                keyboardType="number-pad"
                secureTextEntry={!showNewPin}
                maxLength={6}
                placeholder="Enter new PIN"
                placeholderTextColor="#777777"
              />

              <TouchableOpacity
                style={styles.pinVisibilityButton}
                onPress={() =>
                  setShowNewPin(
                    value => !value,
                  )
                }
                accessibilityRole="button"
                accessibilityLabel="Toggle new PIN visibility">

                {!showNewPin ? (
                  <SvgUri
                    uri={Image.resolveAssetSource(EYE_OFF_ICON).uri}
                    width={26}
                    height={24}
                  />
                ) : (
                  <SvgUri
                    uri={Image.resolveAssetSource(EYE_ON_ICON).uri}
                    width={32}
                    height={24}
                  />
                )}

              </TouchableOpacity>

            </View>

            <Text
              style={styles.inputLabel}>
              Confirm New PIN
            </Text>

            <View
              style={styles.pinInputWrapper}>

              <TextInput
                style={styles.changePinInput}
                value={confirmNewPin}
                onChangeText={setConfirmNewPin}
                keyboardType="number-pad"
                secureTextEntry={!showConfirmNewPin}
                maxLength={6}
                placeholder="Confirm new PIN"
                placeholderTextColor="#777777"
              />

              <TouchableOpacity
                style={styles.pinVisibilityButton}
                onPress={() =>
                  setShowConfirmNewPin(
                    value => !value,
                  )
                }
                accessibilityRole="button"
                accessibilityLabel="Toggle confirm PIN visibility">

                {!showConfirmNewPin ? (
                  <SvgUri
                    uri={Image.resolveAssetSource(EYE_OFF_ICON).uri}
                    width={26}
                    height={24}
                  />
                ) : (
                  <SvgUri
                    uri={Image.resolveAssetSource(EYE_ON_ICON).uri}
                    width={32}
                    height={24}
                  />
                )}

              </TouchableOpacity>

            </View>

            <View
              style={styles.pinButtonRow}>

              <TouchableOpacity
                style={styles.cancelButton}
                onPress={resetChangePinForm}>

                <Text
                  style={styles.cancelButtonText}>
                  Cancel
                </Text>

              </TouchableOpacity>

              <TouchableOpacity
                style={styles.savePinButton}
                onPress={handleChangePin}
                disabled={changingPin}>

                <Text
                  style={styles.savePinButtonText}>
                  {changingPin
                    ? 'Changing...'
                    : 'Change PIN'}
                </Text>

              </TouchableOpacity>

            </View>

          </View>

        </View>

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
        style={styles.bottomNav}>

        <TouchableOpacity
          style={styles.bottomNavItem}
          onPress={() =>
            changeSection('home')
          }>
          <View
            style={[
              styles.bottomNavPill,
              section === 'home' &&
                styles.bottomNavPillActive,
            ]}>
            <NavIcon
              name="home"
              active={section === 'home'}
            />
            <Text
              style={[
                styles.bottomNavText,
                section === 'home' &&
                  styles.bottomNavTextActive,
              ]}>
              Home
            </Text>
          </View>
        </TouchableOpacity>

        <TouchableOpacity
          style={styles.bottomNavItem}
          onPress={() =>
            changeSection('locked')
          }>
          <View
            style={[
              styles.bottomNavPill,
              section === 'locked' &&
                styles.bottomNavPillActive,
            ]}>
            <NavIcon
              name="locked"
              active={section === 'locked'}
            />
            <Text
              style={[
                styles.bottomNavText,
                section === 'locked' &&
                  styles.bottomNavTextActive,
              ]}>
              Locked Apps
            </Text>
          </View>
        </TouchableOpacity>

        <TouchableOpacity
          style={styles.bottomNavItem}
          onPress={() =>
            changeSection('notifications')
          }>
          <View
            style={[
              styles.bottomNavPill,
              section === 'notifications' &&
                styles.bottomNavPillActive,
            ]}>
            <NavIcon
              name="hide"
              active={section === 'notifications'}
            />
            <Text
              style={[
                styles.bottomNavText,
                section === 'notifications' &&
                  styles.bottomNavTextActive,
              ]}>
              Hide
            </Text>
          </View>
        </TouchableOpacity>

        <TouchableOpacity
          style={styles.bottomNavItem}
          onPress={() =>
            changeSection('lockApps')
          }>
          <View
            style={[
              styles.bottomNavPill,
              section === 'lockApps' &&
                styles.bottomNavPillActive,
            ]}>
            <NavIcon
              name="apps"
              active={section === 'lockApps'}
            />
            <Text
              style={[
                styles.bottomNavText,
                section === 'lockApps' &&
                  styles.bottomNavTextActive,
              ]}>
              Apps
            </Text>
          </View>
        </TouchableOpacity>

        <TouchableOpacity
          style={styles.bottomNavItem}
          onPress={() =>
            changeSection('settings')
          }>
          <View
            style={[
              styles.bottomNavPill,
              section === 'settings' &&
                styles.bottomNavPillActive,
            ]}>
            <NavIcon
              name="settings"
              active={section === 'settings'}
            />
            <Text
              style={[
                styles.bottomNavText,
                section === 'settings' &&
                  styles.bottomNavTextActive,
              ]}>
              Settings
            </Text>
          </View>
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

                  <Image
                    source={{
                      uri: STAT_ICON_LOCK,
                    }}
                    style={styles.statImage}
                  />

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

                  <Image
                    source={{
                      uri: STAT_ICON_BELL,
                    }}
                    style={styles.statImage}
                  />

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
                    style={styles.behaviorRadioOuter}>

                    {lockBehavior ===
                      IMMEDIATE && (
                      <View
                        style={styles.behaviorRadioInner}
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
                    style={styles.behaviorRadioOuter}>

                    {lockBehavior ===
                      AFTER_SCREEN_LOCK && (
                      <View
                        style={styles.behaviorRadioInner}
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

              <View
                style={[
                  styles.notificationStatusRow,
                  showLockNotification
                    ? styles.notificationStatusEnabled
                    : styles.notificationStatusDisabled,
                ]}>

                <Text
                  style={[
                    styles.notificationStatusText,
                    showLockNotification
                      ? styles.notificationStatusTextEnabled
                      : styles.notificationStatusTextDisabled,
                  ]}>
                  {showLockNotification
                    ? '✓ Notification access enabled'
                    : '✕ Notification access disabled'}
                </Text>

              </View>



            </View>
          }
        />

      )}

      {section === 'settings' && (

        <FlatList
          data={[]}
          keyExtractor={() =>
            'settings'
          }
          renderItem={null}
          contentContainerStyle={
            styles.settingsList
          }
          ListHeaderComponent={
            <View>

              <View
                style={styles.settingsCard}>

                <TouchableOpacity
                  style={styles.settingsRow}
                  onPress={() => {
                    if (
                      lockType === 'pattern'
                    ) {
                      handleChangeCredentialType(
                        'pattern',
                      );
                      return;
                    }

                    if (
                      lockType === 'password'
                    ) {
                      handleChangeCredentialType(
                        'password',
                      );
                      return;
                    }

                    openChangePinScreen();
                  }}>

                  <Image
                    source={{
                      uri: SECURITY_ICON_SHIELD,
                    }}
                    style={styles.securityRowIcon}
                  />

                  <View
                    style={styles.settingsRowInfo}>

                    <Text
                      style={styles.settingsRowTitle}>
                      Change {getLockTypeTitle(lockType)}
                    </Text>

                  </View>

                  <Text
                    style={styles.settingsRowChevron}>
                    ›
                  </Text>

                </TouchableOpacity>

                <TouchableOpacity
                  style={[
                    styles.settingsRow,
                    styles.settingsRowLast,
                  ]}
                  onPress={() =>
                    setShowLockTypeScreen(true)
                  }>

                  <Image
                    source={{
                      uri: SECURITY_ICON_LOCK,
                    }}
                    style={styles.securityRowIcon}
                  />

                  <View
                    style={styles.settingsRowInfo}>

                    <Text
                      style={styles.settingsRowTitle}>
                      Change Lock Type
                    </Text>

                  </View>

                  <Text
                    style={styles.settingsRowChevron}>
                    ›
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
      paddingHorizontal: 14,
      paddingTop: 12,
      paddingBottom: 8,
    },

    title: {
      fontSize: 23,
      fontWeight: '700',
      color: '#ffffff',
    },

    subtitle: {
      marginTop: 3,
      fontSize: 12,
      color: '#999999',
    },

    bottomNav: {
      position: 'absolute',
      left: 12,
      right: 12,
      bottom: 10,
      height: 74,
      paddingHorizontal: 6,
      paddingVertical: 7,
      borderRadius: 24,
      backgroundColor: '#1b1a1f',
      borderWidth: 1,
      borderColor: '#5b3524',
      flexDirection: 'row',
      alignItems: 'center',
      zIndex: 1000,
      elevation: 20,
    },

    bottomNavItem: {
      flex: 1,
      alignItems: 'center',
      justifyContent: 'center',
    },

    bottomNavPill: {
      minWidth: 64,
      height: 56,
      paddingHorizontal: 7,
      borderRadius: 16,
      alignItems: 'center',
      justifyContent: 'center',
    },

    bottomNavPillActive: {
      backgroundColor: '#252329',
      borderRadius: 16,
      overflow: 'hidden',
    },

    bottomNavImage: {
      width: 23,
      height: 23,
      marginBottom: 1,
    },

    bottomNavText: {
      marginTop: 2,
      fontSize: 9,
      fontWeight: '600',
      color: '#b7b3bc',
    },

    bottomNavTextActive: {
      color: '#ff7518',
    },

    settingsList: {
      paddingTop: 8,
      paddingBottom: 100,
    },

    settingsCard: {
      marginHorizontal: 16,
      paddingHorizontal: 4,
      paddingVertical: 2,
      borderRadius: 14,
      backgroundColor: '#1c1c1c',
    },

    settingsRow: {
      minHeight: 56,
      flexDirection: 'row',
      alignItems: 'center',
      paddingHorizontal: 8,
      borderBottomWidth: 1,
      borderBottomColor: '#2a2a2a',
    },

    settingsRowLast: {
      borderBottomWidth: 0,
    },

    securityRowIcon: {
      width: 26,
      height: 26,
      resizeMode: 'contain',
    },

    settingsRowInfo: {
      flex: 1,
      marginLeft: 12,
    },

    settingsRowTitle: {
      fontSize: 16,
      fontWeight: '600',
      color: '#ffffff',
    },

    settingsRowChevron: {
      fontSize: 28,
      color: '#aaaaaa',
      paddingHorizontal: 6,
    },

    changePinScreen: {
      flex: 1,
      paddingHorizontal: 32,
      justifyContent: 'center',
      backgroundColor: '#0b0b0b',
    },

    changePinHeader: {
      width: '100%',
      minHeight: 56,
      marginBottom: 10,
      flexDirection: 'row',
      alignItems: 'center',
      justifyContent: 'space-between',
    },

    changePinBackButton: {
      position: 'absolute',
      left: 10,
      top: 10,
      width: 48,
      height: 48,
      alignItems: 'center',
      justifyContent: 'center',
      zIndex: 10,
    },

    changePinBackText: {
      fontSize: 34,
      lineHeight: 38,
      color: '#ffffff',
      fontWeight: '300',
    },

    changePinScreenTitle: {
      position: 'absolute',
      left: 0,
      right: 0,
      fontSize: 21,
      fontWeight: '700',
      color: '#ffffff',
      textAlign: 'center',
    },

    changePinHeaderSpacer: {
      width: 44,
      height: 44,
    },

    changePinCard: {
      width: '100%',
      paddingHorizontal: 0,
      paddingVertical: 0,
      backgroundColor: 'transparent',
    },

    pinInputWrapper: {
      position: 'relative',
      width: '100%',
    },

    changePinInput: {
      height: 48,
      paddingLeft: 14,
      paddingRight: 48,
      borderRadius: 10,
      backgroundColor: '#101010',
      borderWidth: 1,
      borderColor: '#3a3a3a',
      color: '#ffffff',
      fontSize: 15,
    },

    pinVisibilityButton: {
      position: 'absolute',
      right: 0,
      top: 0,
      width: 48,
      height: 48,
      alignItems: 'center',
      justifyContent: 'center',
    },

    eyeSvgIcon: {
      width: 38,
      height: 34,
    },

    eyeIcon: {
      width: 20,
      height: 13,
      borderWidth: 1.8,
      borderColor: '#ffffff',
      borderRadius: 10,
      alignItems: 'center',
      justifyContent: 'center',
      transform: [{rotate: '0deg'}],
    },

    eyePupil: {
      width: 5,
      height: 5,
      borderRadius: 3,
      backgroundColor: '#ffffff',
    },

    eyeSlash: {
      position: 'absolute',
      width: 24,
      height: 1.8,
      backgroundColor: '#ffffff',
      transform: [{rotate: '45deg'}],
    },

    changePinPanel: {
      marginHorizontal: 16,
      marginTop: 8,
      paddingHorizontal: 16,
      paddingVertical: 10,
      borderRadius: 12,
      backgroundColor: '#1c1c1c',
    },

    statsRow: {
      flexDirection: 'row',
      marginHorizontal: 12,
      marginBottom: 9,
      gap: 8,
    },

    statCard: {
      flex: 1,
      minHeight: 92,
      paddingVertical: 10,
      borderRadius: 11,
      backgroundColor: '#1c1c1c',
      alignItems: 'center',
    },

    statImage: {
      width: 30,
      height: 30,
      marginBottom: 1,
      resizeMode: 'contain',
    },

    statNumber: {
      marginTop: 2,
      fontSize: 24,
      fontWeight: '700',
      color: '#ffffff',
    },

    statLabel: {
      marginTop: 1,
      fontSize: 10,
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
      marginHorizontal: 12,
      marginBottom: 9,
      paddingHorizontal: 10,
      paddingVertical: 8,
      borderRadius: 11,
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

    behaviorRadioOuter: {
      width: 20,
      height: 20,
      borderRadius: 10,
      borderWidth: 2,
      borderColor: '#888888',
      alignItems: 'center',
      justifyContent: 'center',
    },

    behaviorRadioInner: {
      width: 10,
      height: 10,
      borderRadius: 5,
      backgroundColor: '#ff7518',
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
      marginHorizontal: 12,
      marginBottom: 8,
      paddingHorizontal: 10,
      paddingVertical: 16,
      borderRadius: 12,
      backgroundColor: '#1c1c1c',
      flexDirection: 'row',
      alignItems: 'center',
    },

    notificationStatusRow: {
      marginHorizontal: 12,
      marginBottom: 8,
      paddingHorizontal: 12,
      paddingVertical: 10,
      borderRadius: 12,
    },

    notificationStatusEnabled: {
      backgroundColor: '#182218',
    },

    notificationStatusDisabled: {
      backgroundColor: '#241f16',
    },

    notificationStatusText: {
      fontSize: 13,
    },

    notificationStatusTextEnabled: {
      color: '#8fd18f',
    },

    notificationStatusTextDisabled: {
      color: '#d6a15f',
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



    lockTypeHeader: {
      minHeight: 64,
      paddingHorizontal: 16,
      flexDirection: 'row',
      alignItems: 'center',
      borderBottomWidth: 1,
      borderBottomColor: '#222222',
    },

    backButton: {
      width: 48,
      height: 48,
      marginLeft: -6,
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

    lockTypeOptionCurrent: {
      opacity: 0.45,
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

    biometricSectionTitle: {
      marginTop: 18,
      marginBottom: 10,
      fontSize: 15,
      fontWeight: '600',
      color: '#d2d2d2',
    },

    biometricSection: {
      paddingHorizontal: 16,
      borderRadius: 12,
      backgroundColor: '#1c1c1c',
    },

    biometricRow: {
      minHeight: 72,
      flexDirection: 'row',
      alignItems: 'center',
    },

    biometricInfo: {
      flex: 1,
    },

    biometricTitle: {
      fontSize: 15,
      fontWeight: '600',
      color: '#ffffff',
    },

    biometricDescription: {
      marginTop: 3,
      fontSize: 12,
      lineHeight: 17,
      color: '#888888',
    },

    biometricDivider: {
      width: 1,
      height: 32,
      marginHorizontal: 12,
      backgroundColor: '#555555',
    },

    biometricSeparator: {
      height: 1,
      backgroundColor: '#2b2b2b',
    },

    lockTypeLoading: {
      paddingVertical: 18,
      alignItems: 'center',
    },

    inputLabel: {
      marginTop: 18,
      marginBottom: 6,
      fontSize: 15,
      color: '#d2d2d2',
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
      marginTop: 18,
      gap: 10,
    },

    cancelButton: {
      flex: 1,
      minHeight: 48,
      paddingVertical: 0,
      borderRadius: 12,
      backgroundColor: '#333333',
      alignItems: 'center',
      justifyContent: 'center',
    },

    cancelButtonText: {
      fontSize: 14,
      fontWeight: '600',
      color: '#ffffff',
    },

    savePinButton: {
      flex: 1,
      minHeight: 48,
      paddingVertical: 0,
      borderRadius: 12,
      backgroundColor: '#ffffff',
      alignItems: 'center',
      justifyContent: 'center',
    },

    savePinButtonText: {
      fontSize: 14,
      fontWeight: '600',
      color: '#101010',
      textAlign: 'center',
      width: '100%',
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