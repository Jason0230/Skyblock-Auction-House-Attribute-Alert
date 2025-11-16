
# Hypixel Skyblock Auction House Attribute Notifier

Scans the Hypixel Skyblock Auction House for user inputted item and attribute. Returns a list with the prices of the wanted items with the command to easily buy them and the price. Allows user to input a maximum price that will play an alert sound notifing the user that there is an item that cost less than the maximum price inputted.


## Installation

Install the AuctionHouseAlert folder

```
  1. Copy the path of the AuctionHouseAlert folder 
  2. Open Windows Terminal and type "cd " plus the file path
        Example: cd C:\Users\user\Documents\AuctionHouseAlert
  3. Then type "java AuctionHouseAlert.java" to run the java file
```
    
## Usage

```
1. How many Attributes? (1 or 2):
    - Allows search for either 1 attribute or 2 attributes
    - Enter 1 or 2

2. Item Name:
    - Enter the name of the item you want to search for
    - Not case-sensitive 

3. Attribute with Lvl (Roman Numerals):
    - Filter the item with the attribute you seek followed by the level number in Roman Numerals
    - Ex. Magic Find V
    - Do not include the Lvl to search for attribute alone
    
4. Max Price to Alert at:
    - If a item with a price less than the inputted value is avaliable then an alert sound will play
    - Supports abbreviations (only one)
        - "k", multiplied by 1,000
        - "m", multipled by 1,000,000
        - "b", multipled by 1,000,000,000
        Ex. 12k = 12000
    - Any invalid string that is inputted will set the max price to 0 (no alert)

```

## Demo Video

[![Watch the Video](http://img.youtube.com/vi/jnIfkoXv5zQ/0.jpg)](http://www.youtube.com/watch?v=jnIfkoXv5zQ)
